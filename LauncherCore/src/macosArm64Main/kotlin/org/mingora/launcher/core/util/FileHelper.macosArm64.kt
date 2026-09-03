package org.mingora.launcher.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSOutputStream
import platform.Foundation.outputStreamToFileAtPath

internal actual object FileHelper {
    @Throws(IllegalStateException::class)
    actual fun delete(path: PlatformFile) {
        val manager = NSFileManager.defaultManager
        if (manager.fileExistsAtPath(path.nsUrl.path ?: error("Invalid source URL"))) {
            manager.removeFileAtPath(path.nsUrl.path ?: error("Invalid source URL"), null)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(IllegalStateException::class)
    actual fun replaceDirectory(
        source: PlatformFile,
        target: PlatformFile
    ) {
        val sourcePath = source.nsUrl.path ?: error("Invalid source URL")
        val targetPath = target.nsUrl.path ?: error("Invalid target URL")
        val manager = NSFileManager.defaultManager

        if (!manager.fileExistsAtPath(sourcePath)) {
            error("Source directory does not exist: $sourcePath")
        }

        val success = if (manager.fileExistsAtPath(targetPath)) {
            manager.replaceItemAtURL(
                originalItemURL = target.nsUrl,
                withItemAtURL = source.nsUrl,
                backupItemName = null,
                options = 0u,
                resultingItemURL = null,
                error = null,
            )
        } else {
            val parentPath = targetPath.substringBeforeLast('/')
            if (!manager.fileExistsAtPath(parentPath)) {
                manager.createDirectoryAtPath(
                    path = parentPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
            }
            manager.moveItemAtURL(source.nsUrl, target.nsUrl, null)
        }

        check(success) { "Unable to atomically publish $sourcePath as $targetPath." }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun insertContentWithPosition(file: PlatformFile, contents: String) {
        val outputStream = NSOutputStream.outputStreamToFileAtPath(file.path, false)
        outputStream.open()
        try {
            val bytes = contents.encodeToByteArray()
            var offset = 0
            while (offset < bytes.size) {
                val written = bytes.usePinned { pinned ->
                    outputStream.write(
                        buffer = pinned.addressOf(offset).reinterpret(),
                        maxLength = (bytes.size - offset).convert(),
                    )
                }.toInt()
                check(written > 0) {
                    "Unable to write wine.inf: ${file.path}"
                }
                offset += written
            }
        } finally {
            outputStream.close()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun ensureParentDirectories(source: String) {
        val parent = source.substringBeforeLast('/')
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(parent)) {
            manager.createDirectoryAtPath(
                parent,
                true,
                null,
                null
            )
        }
    }
}