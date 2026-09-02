package org.mingora.launcher.gameinstall.task

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5
import io.github.vinceglb.filekit.PlatformFile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.GameId.Companion.toGameEntry
import org.mingora.launcher.core.util.FileDownloader
import org.mingora.launcher.gameinstall.GameAudioLanguage
import org.mingora.launcher.hyp.HYPClient
import org.mingora.launcher.hyp.models.GameBranch
import org.mingora.launcher.hyp.models.GameChannelSDK
import org.mingora.launcher.hyp.models.GameConfig
import org.mingora.launcher.hyp.models.GameSophonChunkBuild
import org.mingora.launcher.hyp.proto.SophonManifestChunkMode

@OptIn(ExperimentalSerializationApi::class, ExperimentalAtomicApi::class)
internal data class GameBrandNewInstallTask(
    override val installPath: PlatformFile,
    override val audioLanguage: GameAudioLanguage,
    override val gameId: GameId,
    override val gameConfig: GameConfig,
    override val latestGameVersion: String,
    override val gameBranch: GameBranch,
    override val channelSDK: GameChannelSDK?,
    override val localVersionSophonChunkBuild: GameSophonChunkBuild?
) : KoinComponent, GameInstallTask {
    val downloader by inject<FileDownloader>()
    val hypClient by inject<HYPClient>()
    val protoBuf by inject<ProtoBuf>()
    @OptIn(DelicateCryptographyApi::class)
    val md5 = CryptographyProvider.Default.get(MD5).hasher()

    override var taskFiles = mutableListOf<TaskFile>()
    private val currentDownloadedBytesAtomic = AtomicLong(0L)

    val currentDownloadedBytes: Long
        get() = currentDownloadedBytesAtomic.load()
    var totalDownloadedBytes: Long = 0

    fun resetProgress() {
        currentDownloadedBytesAtomic.store(0L)
    }

    override suspend fun prepareFiles() {
        taskFiles.clear()
        totalDownloadedBytes = 0L
        // 准备要下载的文件
        val gameChunkBuildInfo = hypClient.getGameChunkBuild(
            gameInfo = gameId.toGameEntry(),
            gameBranch = gameBranch.main
        ).getOrThrow()
        // 获取游戏本体和选中的音频包 并转换到任务封装的文件类型
        for ((_, _, manifest1, chunkDownload, manifestDownload, _, stats) in availableChunkManifests(gameChunkBuildInfo)) {
            val manifestUrl = manifestDownload.urlPrefix + "/" + manifest1.id
            val manifestBytes = downloader.downloadDirectly(manifestUrl, true).getOrThrow()
            check(md5.hash(manifestBytes).toHexString().equals(manifest1.checksum, ignoreCase = true)) {
                "$manifestUrl is incorrect. Please check again"
            }
            val manifest: SophonManifestChunkMode = protoBuf.decodeFromByteArray(manifestBytes)
            for (singleChunkedFile in manifest.chunks) {
                if (!singleChunkedFile.isFolder) {
                    taskFiles.add(
                        TaskFile.fromSophonChunkFile(
                            singleChunkedFile,
                            installPath,
                            chunkDownload.urlPrefix
                        )
                    )
                }
            }
            totalDownloadedBytes += stats.compressedSize.toLong()
        }
        totalDownloadedBytes += channelSDK?.channelSDKPkg?.size?.toLong() ?: 0L
    }

    override fun increaseProgress(progress: Long) {
        require(progress >= 0L) { "Progress increment must not be negative: $progress" }
        currentDownloadedBytesAtomic.fetchAndAdd(progress)
    }

    override fun onSuccess() {
    }

    override fun onError() {
    }
}
