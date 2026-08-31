package org.mingora.launcher.core.util

import io.github.vinceglb.filekit.PlatformFile

internal expect object CommandExecutor {
    @Throws(IllegalStateException::class)
    fun exec(
        exe: String,
        isSudo: Boolean = false,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        ignoreCode: Boolean = false,
    )

    @Throws(IllegalStateException::class)
    fun exec(
        exe: PlatformFile,
        isSudo: Boolean = false,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        ignoreCode: Boolean = false,
    )

    fun hasExeFile(exe: String): Boolean
}