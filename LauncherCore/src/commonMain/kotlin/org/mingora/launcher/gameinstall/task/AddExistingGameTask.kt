package org.mingora.launcher.gameinstall.task

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
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
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class, ExperimentalSerializationApi::class)
internal data class AddExistingGameTask(
    override val installPath: PlatformFile,
    override val audioLanguage: GameAudioLanguage,
    override val gameId: GameId,
    override val gameConfig: GameConfig,
    override val latestGameVersion: String,
    override val gameBranch: GameBranch,
    override val channelSDK: GameChannelSDK?,
    override val localVersionSophonChunkBuild: GameSophonChunkBuild?,
    override val taskFiles: MutableList<TaskFile> = mutableListOf(),
) : KoinComponent, GameInstallTask {
    private val downloader by inject<FileDownloader>()
    private val hypClient by inject<HYPClient>()
    private val protoBuf by inject<ProtoBuf>()
    @OptIn(DelicateCryptographyApi::class)
    private val md5 = CryptographyProvider.Default.get(MD5).hasher()

    override val currentDownloadedBytesAtomic = AtomicLong(0L)

//    val currentDownloadedBytes: Long
//        get() = currentDownloadedBytesAtomic.load()
    override var totalDownloadedBytes: Long = 0

    fun resetProgress() {
        currentDownloadedBytesAtomic.store(0L)
    }

    override suspend fun prepareFiles() {
        taskFiles.clear()
        totalDownloadedBytes = 0L
        if (!installPath.exists()) {
            throw Exception("Install path does not exist")
        }
        val gameChunkBuildInfo = hypClient.getGameChunkBuild(
            gameInfo = gameId.toGameEntry(),
            gameBranch = gameBranch.main
        ).getOrThrow()

        for ((_, _, manifest1, chunkDownload, manifestDownload) in availableChunkManifests(gameChunkBuildInfo)) {
            val manifestUrl = manifestDownload.urlPrefix + "/" + manifest1.id
            val manifestBytes = downloader.downloadDirectly(manifestUrl, true).getOrThrow()
            check(md5.hash(manifestBytes).toHexString().equals(manifest1.checksum, ignoreCase = true)) {
                "$manifestUrl is incorrect. Please check again"
            }
            val singleManifest: SophonManifestChunkMode = protoBuf.decodeFromByteArray(manifestBytes)
            for (singleFile in singleManifest.chunks) {
                if (singleFile.isFolder) continue
                val path = installPath.resolve(singleFile.file)
                if (path.exists() && path.isRegularFile() && path.size() == singleFile.size &&
                    (singleFile.md5.isBlank() || fileMd5(path) == singleFile.md5.lowercase())) {
                    continue
                }
                taskFiles.add(
                    TaskFile.fromSophonChunkFile(
                        singleFile,
                        installPath = installPath,
                        chunkDownload.urlPrefix
                    )
                )
                totalDownloadedBytes += singleFile.size
            }
        }
    }

    override fun increaseProgress(progress: Long) {
        require(progress >= 0L) { "Progress increment must not be negative: $progress" }
        currentDownloadedBytesAtomic.fetchAndAdd(progress)
    }

    private suspend fun fileMd5(destination: PlatformFile): String {
        val hashFunction = md5.createHashFunction()
        return withContext(Dispatchers.Default) {
            destination.source().buffered().use { source ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = source.readAtMostTo(buffer)
                    if (read <= 0) break
                    hashFunction.update(buffer, 0, read)
                }
            }
            hashFunction.hashToByteArray().toHexString().lowercase()
        }
    }
}
