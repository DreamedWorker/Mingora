package org.mingora.launcher.wine

import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.writeString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.Consts

object WineService : KoinComponent {
    private val installer by inject<WineInstaller>()
    private val wrapper = WineWrapper(Consts.wineBinaryDir, Consts.winePrefix)

    /**
     * 安装运行环境。
     *
     * 将 Result.failure 转换为异常，方便 Swift 通过 completionHandler 获取失败原因。
     */
    @Throws(Exception::class)
    suspend fun installEnv(
        useMirror: Boolean,
        onProgress: (resource: String, downloaded: Long, total: Long?) -> Unit,
        onPostConfiguration: (prefixDirPath: String) -> String?,
    ) {
        // 下载和安装 wine
        installer.installAndConfigureWine(useMirror, onProgress).getOrThrow()
        // 基础配置
        onProgress("正在为 wine 配置 prefix 目录", 1, null)
        wrapper.exec("wineboot", listOf("-u"))
        wrapper.exec("winecfg", listOf("-v", "win10"))
        setupNVExtension()

        // 资源文件位于 App Bundle 中，因此由 App 在基础配置完成后执行复制。
        val copyError = onPostConfiguration(Consts.winePrefix.path)
        check(copyError == null) {
            copyError ?: "复制 Wine 运行库文件失败。"
        }
    }

    private suspend fun setupNVExtension() {
        val command = """
            @echo off
            cd "%~dp0"
            reg add "HKEY_LOCAL_MACHINE\\SOFTWARE\\NVIDIA Corporation\\Global" /v "{41FCC608-8496-4DEF-B43E-7D9BD675A6FF}" /t REG_BINARY /d 1 /f
            reg add "HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Services\\nvlddmkm" /v "{41FCC608-8496-4DEF-B43E-7D9BD675A6FF}" /t REG_BINARY /d 1 /f
            reg add "HKEY_LOCAL_MACHINE\\SOFTWARE\\NVIDIA Corporation\\Global\\NGXCore" /v FullPath /t REG_SZ /d "C:\\Windows\\System32" /f
        """.trimIndent()
        val file = Consts.wineBinaryDir.resolve("driver_config.bat")
        file.writeString(command)
        wrapper.cmd(
            args = listOf("/c", wrapper.toWinePath(file.path))
        )
        file.delete()
    }
}