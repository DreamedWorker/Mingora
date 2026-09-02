package org.mingora.launcher.gameinstall

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.io.buffered
import org.mingora.launcher.core.util.FileDownloader
import org.mingora.launcher.core.zstd.ZstdStreamDecompressor
import org.mingora.launcher.gameinstall.task.GameInstallTask
import org.mingora.launcher.gameinstall.task.TaskFile

@OptIn(DelicateCryptographyApi::class)
internal class GameInstallHelper(
    private val downloader: FileDownloader,
    private val zstd: ZstdStreamDecompressor,
) {
    private val md5Hasher = CryptographyProvider.Default.get(MD5).hasher()

    suspend fun downloadChunksToFile(
        task: GameInstallTask,
        file: TaskFile
    ) {
        // 跳过已存在的文件
        if (file.fullPath.exists() && file.fullPath.isRegularFile() && file.fullPath.size() == file.size &&
            (file.md5.isBlank() || fileMd5(file.fullPath) == file.md5.lowercase())) {
            task.increaseProgress(file.size)
            return
        }

        val output = ByteArray(file.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        for ((id, offset1, compressedSize, uncompressedSize, compressedMd5, uncompressedMd5, url) in file.chunks.sortedBy { it.offset }) {
            val temporary = PlatformFile("${file.fullPath.path}.chunk-$id.part")
            downloadToFile(task, temporary, url, compressedSize, compressedMd5)
            val compressed = temporary.readBytes()
            temporary.delete(mustExist = false)
            val uncompressed = if (compressedSize == uncompressedSize) compressed else zstd.decompress(compressed).output
            check(uncompressedMd5.isBlank() || digest(uncompressed) == uncompressedMd5.lowercase()) {
                "Chunk verification failed: $id"
            }
            val offset = offset1.coerceAtMost(output.size.toLong()).toInt()
            check(offset + uncompressed.size <= output.size) { "Chunk exceeds target file: ${file.nameWithRelativePath}" }
            uncompressed.copyInto(output, destinationOffset = offset)
            task.increaseProgress(uncompressed.size.toLong())
        }
    }

    suspend fun downloadGameChannelSDK(task: GameInstallTask) {
        if (task.channelSDK == null) return
        val sdk = task.channelSDK!!
        val config = task.gameConfig
        val destination = task.installPath
            .resolve(config.wpfExeDir)
            .resolve(sdk.pkgVersionFileName.ifBlank { "channel_sdk_${sdk.version}.zip" })
        downloadToFile(
            task,
            destination,
            sdk.channelSDKPkg.url,
            sdk.channelSDKPkg.size.toLong(),
            sdk.channelSDKPkg.md5
        )
    }

    suspend fun downloadToFile(
        task: GameInstallTask,
        destination: PlatformFile,
        url: String,
        expectedSize: Long,
        expectedMd5: String,
    ) {
        if (destination.exists() && destination.isRegularFile() && destination.size() == expectedSize &&
            (expectedMd5.isBlank() || fileMd5(destination) == expectedMd5.lowercase())) {
            task.increaseProgress(expectedSize)
            return
        }

        var previousBytes = 0L
        val result = downloader.download(url, destination) { bytes, _ ->
            task.increaseProgress((bytes - previousBytes).coerceAtLeast(0L))
            previousBytes = bytes
        }
        result.getOrThrow()

        check(expectedSize <= 0 || destination.size() == expectedSize) {
            "Downloaded file has an unexpected size: ${destination.path}"
        }
        check(expectedMd5.isBlank() || fileMd5(destination) == expectedMd5.lowercase()) {
            "Downloaded file has an unexpected MD5: ${destination.path}"
        }
    }

    private fun fileMd5(destination: PlatformFile): String {
        val hashFunction = md5Hasher.createHashFunction()
        destination.source().buffered().use { source ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = source.readAtMostTo(buffer)
                if (read <= 0) break
                hashFunction.update(buffer, 0, read)
            }
        }
        return hashFunction.hashToByteArray().toHexString().lowercase()
    }

    private suspend fun digest(bytes: ByteArray): String = md5Hasher.hash(bytes).toHexString().lowercase()
}