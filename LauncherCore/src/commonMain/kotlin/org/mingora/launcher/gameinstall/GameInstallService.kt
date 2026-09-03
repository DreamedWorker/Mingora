package org.mingora.launcher.gameinstall

import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.buffered
import org.mingora.launcher.app.GameInstallStatus
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.GameId.Companion.isBilibiliServer
import org.mingora.launcher.core.preference.LauncherPreference
import org.mingora.launcher.core.util.DeviceUtil
import org.mingora.launcher.gameinstall.task.GameBrandNewInstallTask
import org.mingora.launcher.gameinstall.task.GameInstallTask
import kotlin.coroutines.cancellation.CancellationException

internal class GameInstallService(
    private val helper: GameInstallHelper,
) {
    private val taskLock = Mutex()
    private val currentTask = mutableMapOf<GameId, GameInstallTask>()
    private val activeJobs = mutableMapOf<GameId, Job>()
    private val pendingControls = mutableMapOf<GameId, TaskControl>()
    private val taskStates = mutableMapOf<GameId, String>()
    private val taskProgress = mutableMapOf<GameId, Pair<Long, Long>>()
    private val taskScope = CoroutineScope(Dispatchers.Default)
    // 一个文件对应多个 HTTP chunk。限制并发请求数，避免一次安装创建数百个
    // NSURLSession task，导致 CDN/系统连接池大量超时。
    private val downloadLimiter = Semaphore(DeviceUtil.getCpuCoreCount())

    private enum class TaskControl {
        Pause,
        Terminate,
    }

    suspend fun insertTask(task: GameInstallTask) {
        taskLock.withLock {
            if (currentTask.isNotEmpty()) {
                throw IllegalStateException("Already inserting task")
            }
            if (currentTask.contains(task.gameId)) {
                throw IllegalArgumentException("The task of this game already exists")
            }
            currentTask[task.gameId] = task
            taskStates[task.gameId] = "preparing"
            taskProgress[task.gameId] = 0L to 0L
        }
    }

    /**
     * 将安装任务提交到服务自己的生命周期中。
     *
     * 不能依赖调用方的 CoroutineContext：Swift 调用 suspend API 时，Kotlin/Native
     * 的 continuation 不一定携带 Job。任务本身必须由 service scope 持有，否则会在
     * Swift -> Kotlin interop 场景下抛出 “Game installation must run in a coroutine”。
     */
    suspend fun startDownloadTask(gameId: GameId) {
        val task = taskLock.withLock {
            val task = currentTask[gameId]
                ?: throw IllegalArgumentException("The task of this game does not exists")
            check(activeJobs[gameId] == null) { "The task of this game is already running" }
            task
        }
        val job = taskScope.launch(start = CoroutineStart.LAZY) {
            runDownloadTask(gameId, task)
        }
        taskLock.withLock { activeJobs[gameId] = job }
        job.start()
    }

    private suspend fun runDownloadTask(gameId: GameId, task: GameInstallTask) {
        try {
            task.installPath.createDirectories()
            GameAudioLanguage.setAudioLanguage(task)
            task.prepareFiles()
            taskLock.withLock { taskStates[gameId] = "downloading" }

            when(task) {
                is GameBrandNewInstallTask -> executeInstallTaskAsync(task)
            }
            task.onSuccess()
            taskLock.withLock {
                taskStates[gameId] = "completed"
                taskProgress[gameId] = taskProgress(task)
                currentTask.remove(gameId)
            }
            LauncherPreference.setValue(
                stringPreferencesKey("game_exec_${gameId.id}"),
                task.installPath.resolve(task.gameConfig.exeFileName).path,
            )
        } catch (error: CancellationException) {
            val control = taskLock.withLock { pendingControls[gameId] }
            if (control == null) {
                println(error)
                task.onError()
            }
            taskLock.withLock {
                if (control == TaskControl.Pause) {
                    taskStates[gameId] = "paused"
                    taskProgress[gameId] = taskProgress(task)
                } else if (control == TaskControl.Terminate) {
                    taskStates[gameId] = "terminated"
                    taskProgress[gameId] = taskProgress(task)
                    currentTask.remove(gameId)
                }
            }
        } catch (error: Throwable) {
            error.printStackTrace()
            task.onError()
            taskLock.withLock {
                taskStates[gameId] = "failed"
                taskProgress[gameId] = taskProgress(task)
                currentTask.remove(gameId)
            }
        } finally {
            taskLock.withLock {
                if (activeJobs[gameId] === currentCoroutineContext()[Job]) {
                    activeJobs.remove(gameId)
                    pendingControls.remove(gameId)
                }
            }
        }
    }

    suspend fun pauseDownloadTask(gameId: GameId) {
        val job = taskLock.withLock {
            check(currentTask.containsKey(gameId)) { "The task of this game does not exists" }
            activeJobs[gameId]?.also { pendingControls[gameId] = TaskControl.Pause }
        }
        job?.cancelAndJoin()
    }

    suspend fun resumeDownloadTask(gameId: GameId) {
        val task = taskLock.withLock {
            currentTask[gameId]
                ?: throw IllegalArgumentException("The task of this game does not exists")
        }
        taskLock.withLock {
            check(activeJobs[gameId] == null) { "The task of this game is already running" }
        }
        (task as? GameBrandNewInstallTask)?.resetProgress()
        startDownloadTask(gameId)
    }

    suspend fun terminateDownloadTask(gameId: GameId) {
        val job = taskLock.withLock {
            check(currentTask.containsKey(gameId)) { "The task of this game does not exists" }
            activeJobs[gameId]?.also { pendingControls[gameId] = TaskControl.Terminate }
        }
        if (job != null) {
            job.cancelAndJoin()
        } else {
            taskLock.withLock {
                currentTask.remove(gameId)
                taskStates[gameId] = "terminated"
            }
        }
    }

    suspend fun getStatus(gameId: GameId, isInstalled: Boolean): GameInstallStatus = taskLock.withLock {
        val task = currentTask[gameId]
        val state = taskStates[gameId] ?: if (isInstalled) "completed" else "notInstalled"
        val (downloaded, total) = if (task != null) {
            taskProgress(task)
        } else {
            taskProgress[gameId] ?: (0L to 0L)
        }
        GameInstallStatus(state, downloaded, total)
    }

    private fun taskProgress(task: GameInstallTask): Pair<Long, Long> {
        val installTask = task as? GameBrandNewInstallTask
            ?: return 0L to 0L
        return installTask.currentDownloadedBytes to installTask.totalDownloadedBytes
    }

    private suspend fun executeInstallTaskAsync(task: GameBrandNewInstallTask) {
        executeInstallTaskDownloadModeChunk(task)
        // macOS 下打开千星沙箱当前会导致游戏白屏，需要强行停止并在下次启动时快速退出编辑器页面；
        // 而当前仅 Genshin 需要下载 WPF 包，因此跳过该步骤。
        downloadGameChannelSDK(task)
        setGameConfigIni(task)
    }

    private suspend fun executeInstallTaskDownloadModeChunk(task: GameInstallTask) {
        val files = task.taskFiles
        coroutineScope {
            files.map { file ->
                launch(Dispatchers.Default) {
                    downloadLimiter.withPermit {
                        helper.downloadChunksToFile(task, file)
                        file.isFinished = true
                    }
                }
            }.joinAll()
        }
    }

    private suspend fun downloadGameChannelSDK(task: GameInstallTask) {
        helper.downloadGameChannelSDK(task)
    }

    private suspend fun setGameConfigIni(context: GameInstallTask, vararg overrides: Pair<String, String?>) {
        val path = context.installPath.resolve("config.ini")
        val values = linkedMapOf<String, String>()
        if (path.exists()) {
            Regex("(?m)^\\s*([^#;\\r\\n=]+)\\s*=\\s*([^\\r\\n]*)").findAll(path.readString()).forEach {
                values[it.groupValues[1].trim()] = it.groupValues[2].trim()
            }
        }
        context.latestGameVersion.takeIf { context !is GameBrandNewInstallTask }?.let { values["game_version"] = it }
        context.channelSDK?.let { sdk ->
            values["sdk_version"] = sdk.version
            if (context.gameId.isBilibiliServer()) {
                values["channel"] = "14"
                values["sub_channel"] = "0"
            } else {
                values["channel"] = "1"
                values["sub_channel"] = "1"
            }
            values["cps"] = if (context.gameId.isBilibiliServer()) "bilibili" else "mihoyo"
        }
        overrides.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        path.parent()?.also { if (!it.exists()) it.createDirectories() }
        path.sink().buffered().use { sink ->
            values.entries.joinToString("\n") { "${it.key}=${it.value}" }.encodeToByteArray().let { sink.write(it, endIndex = it.size) }
            sink.flush()
        }
    }
}