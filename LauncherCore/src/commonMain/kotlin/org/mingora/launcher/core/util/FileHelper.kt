package org.mingora.launcher.core.util

import io.github.vinceglb.filekit.PlatformFile

internal expect object FileHelper {
    @Throws(IllegalStateException::class)
    fun delete(path: PlatformFile)

    @Throws(IllegalStateException::class)
    fun replaceDirectory(source: PlatformFile, target: PlatformFile)

    fun insertContentWithPosition(file: PlatformFile, contents: String)
}