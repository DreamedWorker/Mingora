package org.mingora.launcher.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
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
        val temporaryFile = PlatformFile("${destination.path}.part")

        return try {
            destination.parent()?.let { parent ->
                if (!parent.exists()) {
                    parent.createDirectories()
                }
            }
            temporaryFile.delete(mustExist = false)

            httpClient.prepareGet(url).execute { response ->
                if (response.status.value !in 200..299) {
                    throw DownloadException(
                        "下载失败，服务器返回 HTTP ${response.status.value} (${response.status.description})。",
                    )
                }

                val totalBytes = response.contentLength()
                var downloadedBytes = 0L
                onProgress(downloadedBytes, totalBytes)

                temporaryFile.sink()
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
            runCatching { temporaryFile.delete(mustExist = false) }
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

    private companion object {
        const val BUFFER_SIZE_BYTES = 1024 * 1024
    }
}
