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
import org.mingora.launcher.core.HYPLauncherId.Companion.isBiliLauncher
import org.mingora.launcher.core.exception.HYPApiException
import org.mingora.launcher.hyp.models.GameBackground
import org.mingora.launcher.hyp.models.GameBackgroundInfoWrapper
import org.mingora.launcher.hyp.models.GameBranch
import org.mingora.launcher.hyp.models.GameBranchesWrapper
import org.mingora.launcher.hyp.models.GameChannelSDK
import org.mingora.launcher.hyp.models.GameChannelSDKWrapper
import org.mingora.launcher.hyp.models.GameConfig
import org.mingora.launcher.hyp.models.GameConfigsWrapper
import org.mingora.launcher.hyp.models.GameContent
import org.mingora.launcher.hyp.models.GameContentWrapper
import org.mingora.launcher.hyp.models.GameInfo
import org.mingora.launcher.hyp.models.GameInfoWrapper
import org.mingora.launcher.hyp.models.GameSophonChunkBuild
import org.mingora.launcher.hyp.models.GameSophonChunkPatch
import org.mingora.launcher.hyp.models.HYPApiWrapper
import org.mingora.launcher.hyp.models.WPFPackage
import org.mingora.launcher.hyp.models.common.SimpleGameEntry

internal class HYPClient(private val httpClient: HttpClient) {
    /**
     * 游戏信息（图标、背景图等）*/
    suspend fun getGameInfo(launcherId: HYPLauncherId): Result<List<GameInfo>> {
        val url = buildUrl("getGames", launcherId)
        val result = commonGet<GameInfoWrapper>(url).getOrElse { return Result.failure(it) }
        return Result.success(result.games)
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

    /**
     * 指定游戏的咨询窗轮播图、社区官方文章
     *
     * @param gameId 游戏ID枚举值
     * */
    suspend fun getGameContent(gameId: GameId, launcherId: HYPLauncherId): Result<GameContent> {
        val url = "${buildUrl("getGameContent", launcherId)}&gameId=${gameId.id}"
        val result = commonGet<GameContentWrapper>(url).getOrElse { return Result.failure(it) }
        return Result.success(result.content)
    }

    /**
     * 渠道服SDK
     *
     * @param launcherId 渠道服启动器ID
     * */
    suspend fun getGameChannelSDK(launcherId: HYPLauncherId): Result<GameChannelSDK> {
        val url = with(StringBuilder(buildUrl("getGameChannelSDKs", launcherId))) {
            if (launcherId.isBiliLauncher()) {
                append("&channel=14&sub_channel=0")
            } else {
                append("&channel=1&sub_channel=1")
            }
            toString()
        }
        val result = commonGet<GameChannelSDKWrapper>(url).getOrElse { return Result.failure(it) }
        val sdks = result.gameChannelSdks
        if (sdks.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cannot pass official channel to this function"))
        }
        return Result.success(sdks.first())
    }

    /**
     * 按游戏获取其配置
     *
     * @param gameId 需要获取配置的游戏
     * */
    suspend fun getSingleGameConfig(gameId: GameId, launcherId: HYPLauncherId): Result<GameConfig> {
        val url = buildUrl("getGameConfigs", launcherId)
        val result = commonGet<GameConfigsWrapper>(url).getOrElse { return Result.failure(it) }
        val req = result.launchConfigs.filter { it.game.id == gameId.id }
        return if (req.isNotEmpty()) {
            Result.success(req.first())
        } else {
            Result.failure(IllegalArgumentException("Cannot find the game config by this gameId"))
        }
    }

    /**
     * 按游戏获取其正式和预下载分支
     *
     * @param gameId 需要获取下载分支的游戏
     * */
    suspend fun getSingleGameBranch(gameId: GameId, launcherId: HYPLauncherId): Result<GameBranch> {
        val url = buildUrl("getGameBranches", launcherId)
        val result = commonGet<GameBranchesWrapper>(url).getOrElse { return Result.failure(it) }
        val req = result.gameBranches.filter { it.game.id == gameId.id }
        return if (req.isNotEmpty()) {
            Result.success(req.first())
        } else {
            Result.failure(IllegalArgumentException("Cannot find the game branch by this gameId"))
        }
    }

    /**
     * Chunk 下载模式文件清单
     *
     * @param gameInfo 游戏信息
     * @param gameBranch 前置请求得到 - 清单分支
     * @see [HYPClient.getSingleGameBranch]
     * */
    suspend fun getGameChunkBuild(gameInfo: SimpleGameEntry, gameBranch: GameBranch.Main): Result<GameSophonChunkBuild> {
        val prefix = when {
            GameId.isCNServer(gameInfo.id) -> "https://downloader-api.mihoyo.com/downloader/sophon_chunk/api/getBuild?"
            GameId.isOSServer(gameInfo.id) -> "https://sg-downloader-api.hoyoverse.com/downloader/sophon_chunk/api/getBuild?"
            else -> return Result.failure(IllegalArgumentException("Cannot find the game chunk builds by this server of game"))
        }
        val url = with(StringBuilder(prefix)) {
            append("branch=${gameBranch.branch}")
            append("&")
            append("package_id=${gameBranch.packageID}")
            append("&")
            append("password=${gameBranch.password}")
            toString()
        }
        val result = commonGet<GameSophonChunkBuild>(url).getOrElse { return Result.failure(it) }
        if (result.manifests.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cannot find the game chunk builds by this game"))
        }
        return Result.success(result)
    }

    /**
     * Chunk 下载模式的增量更新补丁文件清单 (POST)
     *
     * @param gameInfo 游戏信息
     * @param gameBranch 前置请求得到 - 清单分支
     * @see [HYPClient.getSingleGameBranch]
     * */
    suspend fun getGameChunkPatchBuild(gameInfo: SimpleGameEntry, gameBranch: GameBranch.Main): Result<GameSophonChunkPatch> {
        val prefix = when {
            GameId.isCNServer(gameInfo.id) -> "https://downloader-api.mihoyo.com/downloader/sophon_chunk/api/getPatchBuild?"
            GameId.isOSServer(gameInfo.id) -> "https://sg-downloader-api.hoyoverse.com/downloader/sophon_chunk/api/getPatchBuild?"
            else -> return Result.failure(IllegalArgumentException("Cannot find the game patch builds by this server of game"))
        }
        val url = with(StringBuilder(prefix)) {
            append("branch=${gameBranch.branch}")
            append("&")
            append("package_id=${gameBranch.packageID}")
            append("&")
            append("password=${gameBranch.password}")
            toString()
        }
        val result = commonPost<GameSophonChunkPatch>(url) {}.getOrElse { return Result.failure(it) }
        if (result.manifests.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cannot find the game patch builds by this server of game"))
        }
        return Result.success(result)
    }

    /**
     * 按游戏获取 WPF 资源
     *
     * @param gameId 游戏ID
     * */
    suspend fun getSingleGameWPFPackage(gameId: GameId, launcherId: HYPLauncherId): Result<WPFPackage.WPFPackageElement> {
        val url = buildUrl("getGameWPFPackage", launcherId)
        val result = commonGet<WPFPackage>(url).getOrElse { return Result.failure(it) }
        val req = result.wpfPackages.filter { it.game.id == gameId.id }
        return if (req.isNotEmpty()) {
            Result.success(req.first())
        } else {
            Result.failure(IllegalArgumentException("Cannot find the wpf package by this gameId"))
        }
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
