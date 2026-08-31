package org.mingora.launcher.service

import androidx.datastore.preferences.core.stringPreferencesKey
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mingora.launcher.Consts
import org.mingora.launcher.core.GameId
import org.mingora.launcher.core.preference.LauncherPreference
import org.mingora.launcher.core.util.CommandExecutor
import org.mingora.launcher.core.util.FileDownloader
import org.mingora.launcher.hyp.models.GameBackground
import kotlin.experimental.ExperimentalNativeApi

object BackgroundService : KoinComponent {
    private val json by inject<Json>()
    private val sha256 = CryptographyProvider.Default.get(SHA256).hasher()
    private val downloader by inject<FileDownloader>()
    private val mutexRegistryMutex = Mutex()
    private val cacheMutexes = mutableMapOf<String, Mutex>()

    @OptIn(ExperimentalNativeApi::class)
    fun hasFFmpegInstalled(): Boolean {
        if (Platform.osFamily == OsFamily.MACOSX) {
            val file = PlatformFile("/opt/homebrew/bin/ffmpeg")
            return file.exists() && file.isRegularFile()
        } else {
            return false
        }
    }

    /**
     * 更新游戏背景信息，当背景显示组件出现，以及切换选择游戏且 NSCache 没有命中时执行
     * */
    @Throws(Exception::class, CancellationException::class)
    suspend fun updateGameBackground(gameId: GameId): GameBackground {
        val key = stringPreferencesKey("game_bg_${gameId.id}_digest")
        val previousDigest = LauncherPreference.getOrDefault(key, "")
        val neoInfo = HoyoApiService.getGameBackgroundByGame(gameId.launcher, gameId)
            .firstOrNull { it.game.id == gameId.id }

        checkNotNull(neoInfo) { "Game background for ${gameId.id} doesn't exist" }
        check(neoInfo.backgrounds.isNotEmpty()) { "No backgrounds for ${gameId.id}" }
        val data = json.encodeToString(neoInfo)
        val neoHash = sha256.hash(data.encodeToByteArray()).toHexString()
        if (previousDigest.isBlank()) {
            cacheGameBackground(neoInfo)
            return neoInfo
        } else if (previousDigest != neoHash) {
            cacheGameBackground(neoInfo)
            return neoInfo
        } else {
            return neoInfo
        }
    }

    /**
     * 缓存并获取当前游戏的背景视频
     * */
    @Throws(Exception::class, CancellationException::class)
    suspend fun cacheVideoBackground(gameId: String, videoUrl: String): String? {
        // Mutex.withLock 的参数只是 owner 标记，并不会按 gameId 自动分组。
        // 为每个游戏维护独立锁，避免不同游戏的视频缓存互相阻塞。
        val gameCacheMutex = mutexRegistryMutex.withLock {
            cacheMutexes.getOrPut(gameId) { Mutex() }
        }

        return gameCacheMutex.withLock {
            val filename = "${gameId}_${videoUrl}.mp4"
            val nameDigest = sha256.hash(filename.encodeToByteArray()).toHexString()
            val existingFile = Consts.appData.resolve("${gameId}_${nameDigest}.mp4")
            if (existingFile.exists() && existingFile.isRegularFile()) {
                existingFile.path
            } else {
                val oldFiles = Consts.appData.list().filter { it.name.startsWith(gameId) }
                val transcodedMp4File = Consts.appData.resolve("part_${gameId}_${nameDigest}.mp4")
                val webmFile = Consts.appData.resolve("${gameId}.webm")
                try {
                    downloader.download(videoUrl, webmFile).getOrThrow()
                    transcodedMp4File.delete(mustExist = false)
                    CommandExecutor.exec(
                        exe = "/opt/homebrew/bin/ffmpeg",
                        args = listOf(
                            "-y",
                            "-i", webmFile.path,
                            "-c:v", "libx264",
                            "-pix_fmt", "yuv420p",
                            "-an",
                            "-movflags", "+faststart",
                            transcodedMp4File.path,
                        )
                    )
                    check(transcodedMp4File.exists() && transcodedMp4File.isRegularFile()) {
                        "FFmpeg 未生成有效的视频文件。"
                    }
                    transcodedMp4File.atomicMove(existingFile)

                    // 新文件已经原子替换成功后，再清理旧版本。
                    // 失败时保留旧的完整缓存，下一次仍可以继续使用。
                    oldFiles
                        .filter { it != existingFile }
                        .forEach { file ->
                            runCatching { file.delete(mustExist = false) }
                        }
                    existingFile.path
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } finally {
                    webmFile.delete(mustExist = false)
                    transcodedMp4File.delete(mustExist = false)
                }
            }
        }
    }

    private suspend fun cacheGameBackground(gameInfo: GameBackground) {
        val key = stringPreferencesKey("game_bg_${gameInfo.game.id}")
        val digest = stringPreferencesKey("game_bg_${gameInfo.game.id}_digest")
        val data = json.encodeToString(gameInfo)
        val hash = sha256.hash(data.encodeToByteArray()).toHexString()
        LauncherPreference.setValue(key, data)
        LauncherPreference.setValue(digest, hash)
    }
}
