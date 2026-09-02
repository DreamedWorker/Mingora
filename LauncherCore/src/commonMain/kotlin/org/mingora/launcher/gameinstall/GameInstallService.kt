package org.mingora.launcher.gameinstall

import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.sink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.GameId.Companion.isBilibiliServer
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
        }
    }

    suspend fun startDownloadTask(gameId: GameId) {
        val job = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Game installation must run in a coroutine")
        val task = taskLock.withLock {
            val task = currentTask[gameId]
                ?: throw IllegalArgumentException("The task of this game does not exists")
            check(activeJobs[gameId] == null) { "The task of this game is already running" }
            activeJobs[gameId] = job
            pendingControls.remove(gameId)
            task
        }

        try {
            task.installPath.createDirectories()
            GameAudioLanguage.setAudioLanguage(task)
            task.prepareFiles()

            when(task) {
                is GameBrandNewInstallTask -> executeInstallTaskAsync(task)
            }
            task.onSuccess()
        } catch (error: CancellationException) {
            val control = taskLock.withLock { pendingControls[gameId] }
            if (control == null) {
                println(error)
                task.onError()
            }
            if (control == TaskControl.Terminate) {
                taskLock.withLock { currentTask.remove(gameId) }
            }
        } catch (error: Throwable) {
            error.printStackTrace()
            task.onError()
        } finally {
            taskLock.withLock {
                if (activeJobs[gameId] === job) {
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
            taskLock.withLock { currentTask.remove(gameId) }
        }
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
                    helper.downloadChunksToFile(task, file)
                    file.isFinished = true
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