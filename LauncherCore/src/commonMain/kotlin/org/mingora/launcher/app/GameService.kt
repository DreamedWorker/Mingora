package org.mingora.launcher.app

import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.Consts
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.GameId.Companion.isBilibiliServer
import org.mingora.launcher.core.preference.LauncherPreference
import org.mingora.launcher.gameinstall.GameAudioLanguage
import org.mingora.launcher.gameinstall.GameInstallService
import org.mingora.launcher.gameinstall.GameInstallType
import org.mingora.launcher.gameinstall.task.AddExistingGameTask
import org.mingora.launcher.gameinstall.task.GameBrandNewInstallTask
import org.mingora.launcher.gameinstall.task.GameInstallTask
import org.mingora.launcher.hyp.HYPClient
import org.mingora.launcher.wine.WineWrapper

object GameService : KoinComponent {
    private val hypClient by inject<HYPClient>()
    private val gameInstallService by inject<GameInstallService>()
    private val wrapper = WineWrapper(Consts.wineBinaryDir, Consts.winePrefix)

    suspend fun hasGameInstalled(gameId: String): Boolean {
        val key = stringPreferencesKey("game_exec_$gameId")
        val executableFilePath = LauncherPreference.getOrDefault(key, "")
        return executableFilePath.isNotBlank()
    }

    suspend fun getGameInstallStatus(gameId: GameId): GameInstallStatus {
        return gameInstallService.getStatus(gameId, hasGameInstalled(gameId.id))
    }

    suspend fun startGame(gameId: GameId) {
        val key = stringPreferencesKey("game_exec_${gameId.id}")
        val executableFilePath = LauncherPreference.getOrDefault(key, "")
        val commonBackendEnv = mutableMapOf<String, String>()
        commonBackendEnv["GST_PLUGIN_FEATURE_RANK"] = "atdec:MAX,avdec_h264:MAX"
        commonBackendEnv["DXMT_CONFIG"] = "d3d11.preferredMaxFrameRate=60;"
        when(gameId) {
            GameId.HKRPG_CN, GameId.HKRPG_BILIBILI, GameId.HKRPG_GLOBAL -> {
                val args = listOf(executableFilePath, "--", "-disable-gpu-skinning")
                commonBackendEnv["DXMT_CONFIG"] = "d3d11.preferredMaxFrameRate=60;dxgi.customVendorId=10de;dxgi.customDeviceId=2684"
                commonBackendEnv["WINEMSYNC"] = "1"
                commonBackendEnv["DXMT_ENABLE_NVEXT"] = "1"
                wrapper.exec(
                    wrapper.ensureInterExists(),
                    args = args,
                    env = commonBackendEnv,
                    ignoreCode = true
                )
            }
            GameId.HK4E_CN, GameId.HK4E_BILIBILI, GameId.HK4E_GLOBAL -> {
                val args = listOf(executableFilePath)
                commonBackendEnv["WINEESYNC"] = "1"
                wrapper.exec(
                    "C:\\windows\\system32\\steam.exe",
                    args = args,
                    env = commonBackendEnv,
                    ignoreCode = true
                )
            }
            else -> {
                wrapper.exec(
                    executableFilePath,
                    env = commonBackendEnv,
                    ignoreCode = true
                )
            }
        }
    }

    suspend fun installGame(
        gameId: GameId,
        destination: String,
        audioLanguage: GameAudioLanguage,
        installType: GameInstallType
    ) {
        val installPath = PlatformFile(destination)
        ensureDestExists(installPath)
        val task = when(installType) {
            GameInstallType.Install -> generateBrandNewInstallTask(gameId, installPath, audioLanguage)
            GameInstallType.Update -> generateBrandNewInstallTask(gameId, installPath, audioLanguage)
            GameInstallType.PreDownload -> generateBrandNewInstallTask(gameId, installPath, audioLanguage)
            GameInstallType.RegistryExisting -> generateBrandNewInstallTask(gameId, installPath, audioLanguage)
        }
        gameInstallService.insertTask(task)
        gameInstallService.startDownloadTask(gameId)
    }

    @Throws(Exception::class)
    suspend fun pauseGameInstall(gameId: GameId) {
        gameInstallService.pauseDownloadTask(gameId)
    }

    @Throws(Exception::class)
    suspend fun resumeGameInstall(gameId: GameId) {
        gameInstallService.resumeDownloadTask(gameId)
    }

    @Throws(Exception::class)
    suspend fun terminateGameInstall(gameId: GameId) {
        gameInstallService.terminateDownloadTask(gameId)
    }

    private suspend fun generateBrandNewInstallTask(
        gameId: GameId,
        installPath: PlatformFile,
        audioLanguage: GameAudioLanguage,
    ): GameInstallTask {
        val gameConfig = hypClient.getSingleGameConfig(gameId, gameId.launcher).getOrThrow()
        val gameBranch = hypClient.getSingleGameBranch(gameId, gameId.launcher).getOrThrow()
        val channelSDKs = hypClient.getGameChannelSDK(gameId.launcher).getOrNull()
        if (gameId.isBilibiliServer()) {
            requireNotNull(channelSDKs) {
                "Channel SDKs not set, which is not allowed when installing the channel-sever game."
            }
        }
        if (installPath.exists() && installPath.resolve(gameConfig.exeFileName).exists()) {
            return AddExistingGameTask(
                installPath = installPath,
                audioLanguage = audioLanguage,
                gameId = gameId,
                gameConfig = gameConfig,
                gameBranch = gameBranch,
                latestGameVersion = gameBranch.main.tag,
                channelSDK = channelSDKs,
                localVersionSophonChunkBuild = null
            )
        }
        return GameBrandNewInstallTask(
            installPath = installPath,
            audioLanguage = audioLanguage,
            gameId = gameId,
            gameConfig = gameConfig,
            gameBranch = gameBranch,
            latestGameVersion = gameBranch.main.tag,
            channelSDK = channelSDKs,
            localVersionSophonChunkBuild = null
        )
    }

    private fun ensureDestExists(file: PlatformFile) {
        if (!file.exists()) {
            file.createDirectories(true)
        }
    }
}