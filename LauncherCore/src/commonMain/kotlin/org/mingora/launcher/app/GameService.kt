package org.mingora.launcher.app

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.GameId.Companion.isBilibiliServer
import org.mingora.launcher.gameinstall.GameAudioLanguage
import org.mingora.launcher.gameinstall.GameInstallService
import org.mingora.launcher.gameinstall.GameInstallType
import org.mingora.launcher.gameinstall.task.GameBrandNewInstallTask
import org.mingora.launcher.hyp.HYPClient

object GameService : KoinComponent {
    private val hypClient by inject<HYPClient>()
    private val gameInstallService by inject<GameInstallService>()

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
        }
        gameInstallService.insertTask(task)
        gameInstallService.startDownloadTask(gameId)
    }

    private suspend fun generateBrandNewInstallTask(
        gameId: GameId,
        installPath: PlatformFile,
        audioLanguage: GameAudioLanguage,
    ): GameBrandNewInstallTask {
        val gameConfig = hypClient.getSingleGameConfig(gameId, gameId.launcher).getOrThrow()
        val gameBranch = hypClient.getSingleGameBranch(gameId, gameId.launcher).getOrThrow()
        val channelSDKs = hypClient.getGameChannelSDK(gameId.launcher).getOrNull()
        if (gameId.isBilibiliServer()) {
            requireNotNull(channelSDKs) {
                "Channel SDKs not set, which is not allowed when installing the channel-sever game."
            }
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