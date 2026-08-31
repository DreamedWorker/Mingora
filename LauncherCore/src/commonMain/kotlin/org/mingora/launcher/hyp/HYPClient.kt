package org.mingora.launcher.hyp

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.mingora.launcher.Consts
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.HYPLauncherId
import org.mingora.launcher.core.exception.HYPApiException
import org.mingora.launcher.hyp.models.GameBackground
import org.mingora.launcher.hyp.models.GameBackgroundInfoWrapper
import org.mingora.launcher.hyp.models.GameInfo
import org.mingora.launcher.hyp.models.GameInfoWrapper
import org.mingora.launcher.hyp.models.HYPApiWrapper

internal class HYPClient(private val httpClient: HttpClient) {
    /**
     * 游戏信息（图标、背景图等）*/
    suspend fun getGameInfo(launcherId: HYPLauncherId): Result<List<GameInfo>> {
        val url = buildUrl("getGames", launcherId)
        val result = commonGet<GameInfoWrapper>(url).getOrElse { return Result.failure(it) }
        return Result.success(result.games)
    }

    /**
     * 版本背景图和亮点*/
    suspend fun getGameBackground(launcherId: HYPLauncherId): Result<List<GameBackground>> {
        val url = buildUrl("getAllGameBasicInfo", launcherId)
        val result = commonGet<GameBackgroundInfoWrapper>(url).getOrElse { return Result.failure(it) }
        return Result.success(result.gameInfoList)
    }

    /**
     * 按游戏获取版本背景图和亮点
     *
     * @param gameId 游戏ID枚举值
     * */
    suspend fun getGameBackground(gameId: GameId, launcherId: HYPLauncherId): Result<List<GameBackground>> {
        val url = "${buildUrl("getAllGameBasicInfo", launcherId)}&game_id=${gameId.id}"
        val result = commonGet<GameBackgroundInfoWrapper>(url).getOrElse { return Result.failure(it) }
        return Result.success(result.gameInfoList)
    }

    private suspend inline fun <reified T> commonGet(url: String): Result<T> {
        try {
            val resp = httpClient.get(url)
            val result = resp.body<HYPApiWrapper<T>>()
            if (result.code != 0) {
                return Result.failure(HYPApiException(result.code, result.message))
            }
            return Result.success(result.data)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend inline fun <reified T> commonPost(
        url: String,
        block: HttpRequestBuilder.() -> Unit
    ): Result<T> {
        try {
            val resp = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                block()
            }
            val result = resp.body<HYPApiWrapper<T>>()
            if (result.code != 0) {
                return Result.failure(HYPApiException(result.code, result.message))
            }
            return Result.success(result.data)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // 语言参数是 延迟初始化 参数，调用前必须确认初始化工作已经在 NSApplicationDelegate 中被正确调用。
    private fun buildUrl(api: String, launcherId: HYPLauncherId): String {
        return when (launcherId) {
            HYPLauncherId.CHINA_OFFICIAL -> "https://hyp-api.mihoyo.com/hyp/hyp-connect/api/${api}?launcher_id=${launcherId.launcherId}&language=zh-cn"
            HYPLauncherId.GLOBAL_OFFICIAL -> "https://sg-hyp-api.hoyoverse.com/hyp/hyp-connect/api/${api}?launcher_id=${launcherId.launcherId}&language=${Consts.userLanguage}"
            HYPLauncherId.BILIBILI_GENSHIN -> "https://hyp-api.mihoyo.com/hyp/hyp-connect/api/${api}?launcher_id=${launcherId.launcherId}&language=${Consts.userLanguage}"
            HYPLauncherId.BILIBILI_HSR -> "https://hyp-api.mihoyo.com/hyp/hyp-connect/api/${api}?launcher_id=${launcherId.launcherId}&language=${Consts.userLanguage}"
            HYPLauncherId.BILIBILI_NAP -> "https://hyp-api.mihoyo.com/hyp/hyp-connect/api/${api}?launcher_id=${launcherId.launcherId}&language=${Consts.userLanguage}"
        }
    }
}
