package org.mingora.launcher.wine

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import org.mingora.launcher.core.util.CommandExecutor

/**
 * 本地已安装 Wine 的包装器，涵盖主要使用场景的操作封装。
 *
 * @param wineInstallDir 安装目录
 * @param prefixDir PREFIX文件夹
 * */
internal class WineWrapper(
    private val wineInstallDir: PlatformFile,
    private val prefixDir: PlatformFile,
) {
    private val wineBin: PlatformFile
        get() = wineInstallDir.resolve("wine/bin/wine")

    /**
     * 使用 wine 执行命令（不支持sudo）
     * */
    fun exec(
        exe: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        ignoreCode: Boolean = false,
    ) {
        val environment = mutableMapOf<String, String>()
        environment.putAll(genEnvVars())
        environment.putAll(env)
        val arguments = mutableListOf<String>()
        arguments.add(exe)
        arguments.addAll(args)

        CommandExecutor.exec(
            exe = wineBin,
            args = arguments,
            env = environment,
            ignoreCode = ignoreCode,
        )
    }

    /**
     * 使用 windows 的 cmd.exe 执行命令
     * */
    fun cmd(
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
    ) {
        exec("cmd", args, env, false)
    }

    fun waitUntilServerOff() {
        val wineServer = wineInstallDir.resolve("wine/bin/wineserver")
        if (!wineServer.exists()) {
            return
        }
        CommandExecutor.exec(
            wineServer,
            false,
            listOf("-w"),
            genEnvVars(),
            true
        )
    }

    /**
     * 将绝对路径转换成 MS-DOS 风格
     * */
    fun toWinePath(absPath: String): String = "Z:${absPath.replace("/", "\\")}"

    private fun genEnvVars(): Map<String, String> = mapOf("WINEPREFIX" to prefixDir.path)
}