package org.mingora.launcher.core

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.parent
import org.mingora.launcher.core.util.CommandExecutor

internal object TarExtractor {
    fun extract(archiveUrl: PlatformFile, destinationUrl: PlatformFile? = null): Result<Unit> {
        val parent = archiveUrl.parent() ?: return Result.failure(IllegalArgumentException("Invalid archive url"))
        val destination = destinationUrl ?: parent
        val isXZ = archiveUrl.extension == "xz"

        if (!destination.exists()) {
            destination.createDirectories()
        }

        return try {
            CommandExecutor.exec(
                "/usr/bin/tar",
                false,
                listOf(
                    if (isXZ) "-Jxf" else "-zxf",
                    archiveUrl.nsUrl.path!!,
                    "-C",
                    destination.nsUrl.path!!,
                )
            )
            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}