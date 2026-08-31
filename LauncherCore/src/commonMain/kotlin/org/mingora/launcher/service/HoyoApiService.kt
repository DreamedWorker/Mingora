package org.mingora.launcher.service

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.HYPLauncherId
import org.mingora.launcher.hyp.HYPClient
import org.mingora.launcher.hyp.models.GameBackground
import org.mingora.launcher.hyp.models.GameInfo
import kotlin.coroutines.cancellation.CancellationException

object HoyoApiService : KoinComponent {
    private val client by inject<HYPClient>()

    @Throws(Exception::class, CancellationException::class)
    suspend fun getGamesInfo(launcherId: HYPLauncherId): List<GameInfo> {
        val result = client.getGameInfo(launcherId).getOrThrow()
        return result
    }

    @Throws(Exception::class, CancellationException::class)
    suspend fun getGameBackgroundByGame(launcherId: HYPLauncherId, gameId: GameId): List<GameBackground> {
        val result = client.getGameBackground(gameId, launcherId).getOrThrow()
        require(result.firstOrNull() != null) { "Result is null" }
        require(result.first().backgrounds.isNotEmpty()) { "Result is empty" }
        return result
    }
}