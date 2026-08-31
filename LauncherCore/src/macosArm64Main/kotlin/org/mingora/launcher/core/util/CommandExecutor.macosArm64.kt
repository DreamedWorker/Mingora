package org.mingora.launcher.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTask
import platform.Foundation.NSURL
import platform.Foundation.waitUntilExit

internal actual object CommandExecutor {
    @OptIn(ExperimentalForeignApi::class)
    @Throws(IllegalStateException::class)
    actual fun exec(
        exe: String,
        isSudo: Boolean,
        args: List<String>,
        env: Map<String, String>,
        ignoreCode: Boolean) {
        val task = if (isSudo) runInSudo(exe, args, env) else buildTask(exe, args, env)
        check(task.launchAndReturnError(null)) {
            "无法启动或执行命令（$exe）。可执行文件不存在、没有执行权限，或启动参数（args=${args.joinToString(",")}）无效。"
        }
        task.waitUntilExit()

        if (task.terminationStatus != 0 && !ignoreCode) {
            throw IllegalStateException("命令（${exe}）未能正常退出，代码：${task.terminationStatus}。原因：${task.terminationReason}")
        }
    }

    @Throws(IllegalStateException::class)
    actual fun exec(
        exe: PlatformFile,
        isSudo: Boolean,
        args: List<String>,
        env: Map<String, String>,
        ignoreCode: Boolean
    ) {
        exec(exe.nsUrl.path!!, isSudo, args, env, ignoreCode)
    }

    actual fun hasExeFile(exe: String): Boolean {
        val file = PlatformFile(NSURL(string = exe))
        return file.exists() && file.isRegularFile()
    }

    private fun buildTask(
        exe: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): NSTask {
        val environments = mutableMapOf<Any?, Any>()
        NSProcessInfo.processInfo.environment.forEach { entry ->
            val key = entry.key
            val value = entry.value
            if (key != null && value != null) {
                environments[key] = value
            }
        }
        environments.putAll(env)

        val task = NSTask()
        task.environment = environments
        task.setExecutableURL(NSURL.fileURLWithPath(exe))
        task.arguments = args
        println("执行命令：exe=${exe}, args: ${args.joinToString(",")}, env: ${environments.keys}")
        return task
    }

    private fun runInSudo(
        exe: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): NSTask {
        val envStr = env.entries.joinToString(" ") { "${it.key}=${it.value}" }
        val argStr = args.joinToString(" ") { shellEscape(it) }
        val cmd = listOf(
            envStr,
            shellEscape(exe),
            argStr
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val script = """
        do shell script "${
            cmd
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        }" with administrator privileges
        """.trimIndent()
        return buildTask(
            exe = "/usr/bin/osascript",
            args = listOf(
                "-e",
                script
            )
        )
    }

    private fun shellEscape(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}