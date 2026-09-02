package org.mingora.launcher.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.io.buffered
import org.mingora.launcher.core.exception.DownloadException
import org.mingora.launcher.core.zstd.ZstdStreamDecompressor

/**
 * 下载文件，并按字节回报下载进度。
 *
 * 响应将首先被写入到临时文件中，并仅在下载完成后才进行原子移动到 `目标文件`。
 * 确保没有中间态被保留。
 */
internal class FileDownloader(
    private val httpClient: HttpClient,
    private val zstdDecompression: ZstdStreamDecompressor
) {
    /**
     * 将 [url] 下载到 [destination].
     *
     * [onProgress] 接收已下载的字节数和总字节数。
     * 当服务器没有提供 `Content-Length` 头时，后者为 `null`。进度会在读取开始前和每次成功写入一块数据后报告一次。
     */
    @Throws(DownloadException::class, CancellationException::class)
    suspend fun download(
        url: String,
        destination: PlatformFile,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<Unit> {
        val temporaryFile = destination.sibling("${destination.path.substringAfterLast('/')}.part")

        return try {
            ensureParentDirectories(destination)

            var resumeFrom = if (temporaryFile.exists() && temporaryFile.isRegularFile()) {
                temporaryFile.size()
            } else {
                0L
            }

            httpClient.prepareGet(url) {
                if (resumeFrom > 0L) {
                    header(HttpHeaders.Range, "bytes=$resumeFrom-")
                }
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    if (response.status.value == 416 && resumeFrom > 0L) {
                        // 临时文件可能已经收齐，但请求在原子移动前因超时结束，
                        // 也可能是服务端拒绝了这个断点。该偏移无法继续时清除旧
                        // .part，让上层下一次从零开始，避免连续重复得到 416。
                        temporaryFile.delete(mustExist = false)
                    }
                    throw DownloadException(
                        "下载失败，服务器返回 HTTP ${response.status.value} (${response.status.description})。",
                    )
                }

                // 服务器支持 Range 时追加到现有临时文件；如果服务器忽略 Range 返回 200，
                // 必须从头覆盖，否则会把完整响应拼接到旧的前缀后面。
                val append = resumeFrom > 0L && response.status == HttpStatusCode.PartialContent
                if (!append) {
                    if (resumeFrom > 0L) {
                        temporaryFile.delete(mustExist = false)
                    }
                    resumeFrom = 0L
                }

                val responseLength = response.contentLength()
                val totalBytes = responseLength?.let { it + resumeFrom }
                // 回调只报告本次请求新接收的字节数；resumeFrom 已经在之前的
                // 请求中计入任务进度，避免断点续传时重复累计。
                var downloadedBytes = 0L
                onProgress(downloadedBytes, totalBytes)

                temporaryFile.sink(append = append)
                    .buffered()
                    .use { sink ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(BUFFER_SIZE_BYTES)

                        while (true) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead == -1) {
                                break
                            }
                            if (bytesRead == 0) {
                                continue
                            }

                            sink.write(buffer, endIndex = bytesRead)
                            downloadedBytes += bytesRead
                            onProgress(downloadedBytes, totalBytes)
                        }

                        if (totalBytes != null && downloadedBytes != totalBytes) {
                            throw DownloadException(
                                "下载失败，实际下载 $downloadedBytes 字节，期望 $totalBytes 字节。",
                            )
                        }
                        sink.flush()
                    }
            }

            temporaryFile.atomicMove(destination)
            Result.success(Unit)
        } catch (error: Throwable) {
            // 保留 .part 文件。超时、断网或暂停后，下一次请求可以通过 HTTP Range
            // 从已有字节继续，避免进度已经增长但实际数据全部被删除。
            if (error is CancellationException) {
                throw error
            }
            Result.failure(error)
        }
    }

    /**
     * 直接将目标下载到字节数组中，不写入文件系统
     * */
    suspend fun downloadDirectly(
        url: String,
        needDecompression: Boolean = false,
    ): Result<ByteArray> {
        return try {
            val response = httpClient.get(url)
            if (response.status.value !in 200..299) {
                return Result.failure(IllegalStateException("HTTP ${response.status.value} while downloading $url"))
            }
            val originalBytes = response.body<ByteArray>()
            if (needDecompression) {
                val decompressResult = zstdDecompression.decompress(originalBytes)
                if (decompressResult.frameComplete) {
                    Result.success(decompressResult.output)
                } else {
                    Result.failure(Exception("Failed to decompress $url"))
                }
            } else {
                Result.success(originalBytes)
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun PlatformFile.sibling(name: String): PlatformFile {
        val parentPath = parentPathOrNull(path)
            ?: error("Cannot create a sibling file for a path without a parent: $path")
        val siblingPath = if (parentPath == "/") "/$name" else "$parentPath/$name"
        return PlatformFile(siblingPath)
    }

    private fun ensureParentDirectories(file: PlatformFile) {
        parentPathOrNull(file.path)?.let { parentPath ->
            ensureDirectories(PlatformFile(parentPath))
        }
    }

    private fun parentPathOrNull(path: String): String? {
        if (path.isBlank() || path == "/") return null
        val separator = path.lastIndexOf('/')
        if (separator < 0) return null
        return if (separator == 0) "/" else path.substring(0, separator)
    }

    private fun ensureDirectories(directory: PlatformFile) {
        if (directory.exists()) {
            check(directory.isDirectory()) { "Expected directory but found a file: ${directory.path}" }
            return
        }

        // Do not use PlatformFile.parent() here: on FileKit Apple, resolving the
        // parent of "/" creates an NSURL with a null path. Work with absolute path
        // strings and stop explicitly at the filesystem root instead.
        parentPathOrNull(directory.path)?.let { parentPath ->
            ensureDirectories(PlatformFile(parentPath))
        }
        if (directory.exists()) {
            check(directory.isDirectory()) { "Expected directory but found a file: ${directory.path}" }
            return
        }
        runCatching { directory.createDirectories() }
            .onFailure { error ->
                // A parallel download may have created the same directory first.
                if (!directory.exists()) throw error
            }
    }

    private companion object {
        const val BUFFER_SIZE_BYTES = 1024 * 1024
    }
}
