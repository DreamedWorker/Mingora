package org.mingora.launcher.gameinstall

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.MD5
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.Dispatchers
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mingora.launcher.core.util.CommandExecutor
import kotlin.coroutines.cancellation.CancellationException
import org.mingora.launcher.core.util.FileDownloader
import org.mingora.launcher.core.util.FileHelper
import org.mingora.launcher.core.zstd.ZstdStreamDecompressor
import org.mingora.launcher.gameinstall.task.GameInstallTask
import org.mingora.launcher.gameinstall.task.TaskFile
import kotlin.time.Duration.Companion.milliseconds

@OptIn(DelicateCryptographyApi::class)
internal class GameInstallHelper(
    private val downloader: FileDownloader,
    private val zstd: ZstdStreamDecompressor,
) {
    private val md5Hasher = CryptographyProvider.Default.get(MD5).hasher()
    // ZstdStreamDecompressor 持有原生 ZSTD_DCtx，是有状态对象，不能被并发下载任务共享使用。
    private val zstdLock = Mutex()

    suspend fun downloadChunksToFile(
        task: GameInstallTask,
        file: TaskFile
    ) {
        // 跳过已存在的文件
        if (file.fullPath.exists() && file.fullPath.isRegularFile() && file.fullPath.size() == file.size &&
            (file.md5.isBlank() || fileMd5(file.fullPath) == file.md5.lowercase())) {
            task.increaseProgress(file.chunks.sumOf { it.compressedSize })
            return
        }

        // 需要先确保文件的目录链完整
        FileHelper.ensureParentDirectories(task.installPath.path + "/" + file.nameWithRelativePath)

        // 不再按完整文件大小分配 ByteArray。
        // Sophon chunk 按 offset 排序后通常是连续的，因此直接顺序写入目标文件即可。
        //ensureParentDirectories(file.fullPath)
        file.fullPath.sink().buffered().use { sink ->
            var writtenOffset = 0L
            for ((id, offset, compressedSize, uncompressedSize, compressedMd5, uncompressedMd5, url) in
                file.chunks.sortedBy { it.offset }
            ) {
                check(offset >= writtenOffset) {
                    "Chunk is out of order or overlaps the previous chunk: $id"
                }
                writeZeros(sink, offset - writtenOffset)

                val temporary = file.fullPath.sibling("${file.fullPath.path.substringAfterLast('/')}.chunk-$id.part")
                downloadToFile(task, temporary, url, compressedSize, compressedMd5)
                val compressed = temporary.readBytes()
                temporary.delete(mustExist = false)
                val hashFunction = md5Hasher.createHashFunction()
                var uncompressedSizeWritten = 0L
                if (compressedSize == uncompressedSize) {
                    hashFunction.update(compressed, 0, compressed.size)
                    sink.write(compressed, endIndex = compressed.size)
                    uncompressedSizeWritten = compressed.size.toLong()
                } else {
                    val result = zstdLock.withLock {
                        zstd.decompressChunked(compressed) { chunk ->
                            hashFunction.update(chunk, 0, chunk.size)
                            sink.write(chunk, endIndex = chunk.size)
                            uncompressedSizeWritten += chunk.size
                        }
                    }
                    check(result.frameComplete) { "Incomplete zstd chunk: $id" }
                }
                check(uncompressedSizeWritten == uncompressedSize) {
                    "Chunk has an unexpected uncompressed size: $id"
                }
                check(uncompressedMd5.isBlank() || hashFunction.hashToByteArray().toHexString()
                    .equals(uncompressedMd5, ignoreCase = true)) {
                    "Chunk verification failed: $id"
                }
                check(offset + uncompressedSizeWritten <= file.size) {
                    "Chunk exceeds target file: ${file.nameWithRelativePath}"
                }
                writtenOffset = offset + uncompressedSizeWritten
            }
            writeZeros(sink, file.size - writtenOffset)
            sink.flush()
        }

        check(file.fullPath.size() == file.size) {
            "Installed file has an unexpected size: ${file.fullPath.path}"
        }
        check(file.md5.isBlank() || fileMd5(file.fullPath) == file.md5.lowercase()) {
            "Installed file has an unexpected MD5: ${file.fullPath.path}"
        }
    }

    suspend fun downloadGameChannelSDK(task: GameInstallTask) {
        if (task.channelSDK == null) return
        val sdk = task.channelSDK!!
        val destination = task.installPath
            .resolve(sdk.pkgVersionFileName.ifBlank { "channel_sdk_${sdk.version}.zip" })
        downloadToFile(
            task,
            destination,
            sdk.channelSDKPkg.url,
            sdk.channelSDKPkg.size.toLong(),
            sdk.channelSDKPkg.md5
        )
        CommandExecutor.exec(
            "/usr/bin/ditto",
            false,
            listOf("-x", "-k", destination.path, task.installPath.path)
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

        var attempt = 0
        while (true) {
            try {
                var previousBytes = if (destination.exists() && destination.isRegularFile()) {
                    destination.size()
                } else {
                    0L
                }
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
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // 校验失败说明目标文件不是可复用的完整文件；网络超时则保留 .part，
                // 由 FileDownloader 使用 Range 继续下载。
                if (destination.exists() && destination.isRegularFile() &&
                    (expectedSize <= 0L || destination.size() != expectedSize)
                ) {
                    // 这里只清理已经移动到目标路径但校验失败的文件；.part 文件由
                    // FileDownloader 保留并在下一次尝试中续传。
                    destination.delete(mustExist = false)
                }
                if (attempt++ >= MAX_DOWNLOAD_RETRIES) throw error
                delay((RETRY_DELAY_MILLIS * (1L shl attempt.coerceAtMost(3))).milliseconds)
            }
        }
    }

    private fun writeZeros(sink: Sink, count: Long) {
        var remaining = count
        while (remaining > 0L) {
            val length = minOf(remaining, ZERO_BUFFER.size.toLong()).toInt()
            sink.write(ZERO_BUFFER, endIndex = length)
            remaining -= length
        }
    }

    /**
     * 创建文件的临时文件，将下载的子 chunks 写入。
     * */
    private fun PlatformFile.sibling(name: String): PlatformFile {
        println(this)
        val parentPath = this.parent()?.path
            ?: error("Cannot create a sibling file for a path without a parent: $path")
        val siblingPath = if (parentPath == "/") "/$name" else "$parentPath/$name"
        return PlatformFile(siblingPath)
    }

//    private fun ensureParentDirectories(file: PlatformFile) {
//        parentPathOrNull(file.path)?.let { parentPath ->
//            ensureDirectories(PlatformFile(parentPath))
//        }
//    }

//    private fun parentPathOrNull(path: String): String? {
//        if (path.isBlank() || path == "/") return null
//        val separator = path.lastIndexOf('/')
//        if (separator < 0) return null
//        return if (separator == 0) "/" else path.substring(0, separator)
//    }
//
//    private fun ensureDirectories(directory: PlatformFile) {
//        if (directory.exists()) {
//            check(directory.isDirectory()) { "Expected directory but found a file: ${directory.path}" }
//            return
//        }
//
//        // Do not use PlatformFile.parent() here: on FileKit Apple, resolving the
//        // parent of "/" creates an NSURL with a null path. Work with absolute path
//        // strings and stop explicitly at the filesystem root instead.
//        parentPathOrNull(directory.path)?.let { parentPath ->
//            ensureDirectories(PlatformFile(parentPath))
//        }
//        if (directory.exists()) {
//            check(directory.isDirectory()) { "Expected directory but found a file: ${directory.path}" }
//            return
//        }
//        runCatching { directory.createDirectories() }
//            .onFailure { error ->
//                // A parallel download may have created the same directory first.
//                if (!directory.exists()) throw error
//            }
//    }

    private suspend fun fileMd5(destination: PlatformFile): String {
        val hashFunction = md5Hasher.createHashFunction()
        // 因为根任务持有的作用域在 IO 线程，因此执行 CPU 密集的计算任务需要调度
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

    private companion object {
        val ZERO_BUFFER = ByteArray(8192)
        const val MAX_DOWNLOAD_RETRIES = 3
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}