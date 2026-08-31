package org.mingora.launcher.service

import androidx.datastore.preferences.core.stringPreferencesKey
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.HYPLauncherId
import org.mingora.launcher.core.preference.LauncherPreference
import org.mingora.launcher.core.preference.MAINLY_LAUNCHER
import org.mingora.launcher.hyp.models.GameInfo
import kotlin.coroutines.cancellation.CancellationException

object HomeService : KoinComponent {
    private val json by inject<Json>()
    private val sha256 = CryptographyProvider.Default.get(SHA256).hasher()

    /**
     * 在完成Wine初始化后执行，根据选择的启动器地区获取一次对应的游戏信息
     * */
    @Throws(Exception::class, CancellationException::class)
    suspend fun fetchGameInfoAfterConfigure(launcher: HYPLauncherId): List<GameInfo> {
        val games = HoyoApiService.getGamesInfo(launcher)
        games.forEach { game ->
            cacheGameInfo(game)
        }
        LauncherPreference.setValue(MAINLY_LAUNCHER, launcher.launcherId)
        return games
    }

    /**
     * 在已经完成过应用初始化后执行，用于在启动的早期阶段获取游戏信息
     *
     * 这里不应该有联网请求
     * */
    @Throws(Exception::class, CancellationException::class)
    suspend fun getGamesInfoDuringLaunching(launcher: HYPLauncherId): List<GameInfo> {
        val gameIds = GameId.getGameIdsByLauncher(launcher)
        val gameInfoList = mutableListOf<GameInfo>()
        gameIds.forEach { gameId ->
            val key = stringPreferencesKey("game_info_${gameId.id}")
            val temp = LauncherPreference.getOrDefault(key, "")
            if (temp.isNotBlank()) {
                gameInfoList.add(json.decodeFromString(temp))
            }
        }
        return gameInfoList
    }

    /**
     * 更新并获取新的游戏信息。当游戏选择器切换游戏后触发
     * */
    @Throws(Exception::class, CancellationException::class)
    suspend fun getGameInfo(gameId: GameId): GameInfo {
        val key = stringPreferencesKey("game_info_${gameId.id}_digest")
        val previousDigest = LauncherPreference.getOrDefault(key, "")
        val neoInfo = HoyoApiService.getGamesInfo(gameId.launcher)
            .firstOrNull { it.id == gameId.id }

        checkNotNull(neoInfo) { "Game info for ${gameId.id} doesn't exist" }
        val data = json.encodeToString(neoInfo)
        val neoHash = sha256.hash(data.encodeToByteArray()).toHexString()

        if (previousDigest.isBlank()) {
            cacheGameInfo(neoInfo)
            return neoInfo
        } else if (previousDigest != neoHash) {
            cacheGameInfo(neoInfo)
            return neoInfo
        } else {
            return neoInfo
        }
    }

    private suspend fun cacheGameInfo(gameInfo: GameInfo) {
        val key = stringPreferencesKey("game_info_${gameInfo.id}")
        val digest = stringPreferencesKey("game_info_${gameInfo.id}_digest")
        val data = json.encodeToString(gameInfo)
        val hash = sha256.hash(data.encodeToByteArray()).toHexString()
        LauncherPreference.setValue(key, data)
        LauncherPreference.setValue(digest, hash)
    }
}