package org.mingora.launcher.wine

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mingora.launcher.Consts
import org.mingora.launcher.core.TarExtractor
import org.mingora.launcher.core.util.CommandExecutor
import org.mingora.launcher.core.util.FileDownloader
import org.mingora.launcher.core.util.FileHelper
import kotlin.coroutines.cancellation.CancellationException

internal class WineInstaller(
    private val fileDownloader: FileDownloader,
    private val wineInfEditor: WineInfEditor,
) {
    private val installMutex = Mutex()

    @Throws(CancellationException::class, Exception::class)
    suspend fun installAndConfigureWine(
        useMirror: Boolean = false,
        onProgress: (resource: String, downloaded: Long, total: Long?) -> Unit = { _, _, _ -> }
    ): Result<Unit> {
        return installMutex.withLock("WineInstaller") {
            val transactionDir = Consts.downloadDir.resolve(TRANSACTION_DIR_NAME)
            val archiveDir = transactionDir.resolve(ARCHIVE_DIR_NAME)
            val payloadDir = transactionDir.resolve(PAYLOAD_DIR_NAME)
            val wineArchive = archiveDir.resolve(WINE_ARCHIVE_NAME)
            val dxmtArchive = archiveDir.resolve(DXMT_ARCHIVE_NAME)
            val stagedWine = payloadDir.resolve(WINE_DIRECTORY_NAME)
            val stagedDxmt = payloadDir.resolve(DXMT_DIRECTORY_NAME)

            try {
                // 在开始任务之前安全删除之前可能存在的临时目录，不复用过去下载内容
                FileHelper.delete(transactionDir)
                archiveDir.createDirectories(false)
                payloadDir.createDirectories(false)

                // 下载并解压 wine
                downloadAndExtract(
                    remoteUrl = Consts.WINE_URL,
                    archive = wineArchive,
                    extractionDirectory = payloadDir,
                    expectedDirectory = stagedWine,
                    onProgress = onProgress,
                    useMirror = useMirror
                ).getOrThrow()

                // 下载并解压 dxmt
                downloadAndExtract(
                    remoteUrl = Consts.DXMT_URL,
                    archive = dxmtArchive,
                    extractionDirectory = payloadDir,
                    expectedDirectory = stagedDxmt,
                    onProgress = onProgress,
                    useMirror = useMirror
                ).getOrThrow()

                // 修改 wine.inf 必须发生在临时载荷中；失败时正式目录保持不变。
                onProgress("处理根证书", 1, null)
                wineInfEditor.addCertsToWine(stagedWine).getOrThrow()

                // xattr 清理属于安装事务的一部分，必须在临时载荷上完成。
                // 忽略清理失败，即使没有成功系统也会引导用户。
                onProgress("清除隔离属性", 1, null)
                removeXattrAttribute(stagedWine.path)
                removeXattrAttribute(stagedDxmt.path)
                useMetalBackend(stagedWine, stagedDxmt)

                // 两个解压后的文件夹必须整体替换到工作目录，不得分别移动导致原子性破坏。
                onProgress("整理文件", 1, null)
                FileHelper.replaceDirectory(
                    source = payloadDir,
                    target = Consts.wineBinaryDir,
                )

                // 此时，临时目录已空，删除它。
                FileHelper.delete(transactionDir)
                Result.success(Unit)
            } catch (exception: CancellationException) {
                // 用户取消任务，删除临时文件夹
                FileHelper.delete(transactionDir)
                throw exception
            } catch (exception: Exception) {
                // 删除所有的压缩包和任何部分解压的内容。如果没有进行替换，工作目录将保持不变。
                exception.printStackTrace()
                FileHelper.delete(transactionDir)
                Result.failure(exception)
            }
        }
    }

    /**
     * 下载并解压一个文件
     *
     * 将待下载的文件下载到给定的目录下（**必须给出临时目录**），
     * 并将其解压到临时目录。
     *
     * @param remoteUrl 文件类型，用以确定下载URL
     * @param archive 将输入流写入到的文件
     * @param extractionDirectory 解压到的文件夹
     * @param expectedDirectory 解压出的文件夹
     *
     * @return 结果，用于确认在执行过程中是否有错误。
     * */
    private suspend fun downloadAndExtract(
        remoteUrl: String,
        archive: PlatformFile,
        extractionDirectory: PlatformFile,
        expectedDirectory: PlatformFile,
        onProgress: (resource: String, downloaded: Long, total: Long?) -> Unit,
        useMirror: Boolean,
    ): Result<Unit> {
        return try {
            download(
                remoteUrl = remoteUrl,
                destination = archive,
                onProgress = { downloaded, total ->
                    onProgress(
                        remoteUrl.split("/").last(),
                        downloaded,
                        total
                    )
                },
                useMirror = useMirror,
            )
            TarExtractor.extract(archive, extractionDirectory).getOrThrow()
            check(expectedDirectory.exists() && expectedDirectory.isDirectory()) {
                "${archive.name} did not contain ${expectedDirectory.name}."
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            FileHelper.delete(archive)
            throw exception
        } catch (exception: Exception) {
            FileHelper.delete(archive)
            Result.failure(exception)
        }
    }

    private suspend fun download(
        remoteUrl: String,
        destination: PlatformFile,
        useMirror: Boolean = false,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ) {
        val url = if (useMirror) "https://gh-proxy.org/$remoteUrl" else remoteUrl
        fileDownloader.download(url, destination, onProgress)
    }

    private fun removeXattrAttribute(path: String) {
        CommandExecutor.exec(
            "/usr/bin/xattr",
            true,
            listOf("-s", "-r", "-d", "com.apple.quarantine", path),
            ignoreCode = true
        )
    }

    /**
     * 将 DXMT 的文件合并到 Wine 的对应目录中。
     *
     * 使用 `cp -R -f` 复制目录内容，保留目标中的其它文件，并让 DXMT
     * 文件覆盖同名的 Wine 文件。
     */
    private fun useMetalBackend(
        wineDirectory: PlatformFile,
        dxmtDirectory: PlatformFile,
    ) {
        val wineLibWineDirectory = wineDirectory.resolve("lib/wine")
        val backendDirectories = listOf(
            "i386-windows",
            "x86_64-unix",
            "x86_64-windows",
        )

        backendDirectories.forEach { directoryName ->
            val sourceDirectory = dxmtDirectory.resolve(directoryName)
            check(sourceDirectory.exists() && sourceDirectory.isDirectory()) {
                "Could not find DXMT directory: ${sourceDirectory.path}"
            }

            val targetDirectory = wineLibWineDirectory.resolve(directoryName)
            targetDirectory.createDirectories(false)

            CommandExecutor.exec(
                exe = "/bin/cp",
                args = listOf(
                    "-R",
                    "-f",
                    "${sourceDirectory.path}/.",
                    targetDirectory.path,
                ),
            )
        }
    }

    private companion object {
        const val TRANSACTION_DIR_NAME = ".wine-install"
        const val ARCHIVE_DIR_NAME = "archives"
        const val PAYLOAD_DIR_NAME = "payload"
        const val WINE_ARCHIVE_NAME = "wine.tar.xz"
        const val DXMT_ARCHIVE_NAME = "dxmt.tar.gz"
        const val WINE_DIRECTORY_NAME = "wine"
        const val DXMT_DIRECTORY_NAME = "v0.80"
    }
}