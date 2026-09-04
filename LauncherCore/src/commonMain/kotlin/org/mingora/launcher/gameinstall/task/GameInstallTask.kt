package org.mingora.launcher.gameinstall.task

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.resolve
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.SemanticVersion
import org.mingora.launcher.gameinstall.GameAudioLanguage
import org.mingora.launcher.hyp.models.GameBranch
import org.mingora.launcher.hyp.models.GameChannelSDK
import org.mingora.launcher.hyp.models.GameConfig
import org.mingora.launcher.hyp.models.GameSophonChunkBuild
import org.mingora.launcher.hyp.models.GameSophonChunkPatch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * 通用游戏安装相关任务的基接口
 * */
internal sealed interface GameInstallTask {
    val installPath: PlatformFile
    val audioLanguage: GameAudioLanguage

    val gameId: GameId
    val gameConfig: GameConfig
    val latestGameVersion: String
    val gameBranch: GameBranch
    val channelSDK: GameChannelSDK?

    /**
     * 当前运行游戏版本的 Sophon 模型文件清单
     * */
    val localVersionSophonChunkBuild: GameSophonChunkBuild?

    // 任务事件

    val taskFiles: MutableList<TaskFile>

    @OptIn(ExperimentalAtomicApi::class)
    val currentDownloadedBytesAtomic: AtomicLong

    var totalDownloadedBytes: Long

    /**
     * 按照对应的游戏安装模式，将游戏本体和选择的语音包相关的文件转换到封装格式
     * */
    @Throws(Exception::class, CancellationException::class)
    suspend fun prepareFiles()

    fun increaseProgress(progress: Long)

    // 公共实现

    /**
     * 从清单列表中获取可用的 Chunks 清单
     * */
    fun availableChunkManifests(build: GameSophonChunkBuild) =
        build.manifests.filter { it.matchingField == audioLanguage.code || it.matchingField == "game" || it.matchingField.isBlank() }

    /**
     * 从清单列表中获取可用的 Patch Chunks 清单
     * */
    fun availablePatchManifests(patch: GameSophonChunkPatch) =
        patch.manifests.filter { it.matchingField == audioLanguage.code || it.matchingField == "game" || it.matchingField == "patch" }

    /**
     * 从 config.ini 中读取当前安装的游戏版本
     * */
    suspend fun getLocalGameVersion(): SemanticVersion? {
        val config = installPath.resolve("config.ini")
        if (!config.exists() || !config.isRegularFile()) return null
        val value = Regex("(?m)^\\s*game_version\\s*=\\s*([^\\r\\n]+)").find(config.readString())?.groupValues?.get(1)?.trim()
            ?: return null
        return runCatching { SemanticVersion.parse(value) }.getOrNull()
    }

    companion object {
        private val AUDIO_MATCHING_FIELDS = setOf("zh-cn", "en-us", "ja-jp", "ko-kr")
    }
}