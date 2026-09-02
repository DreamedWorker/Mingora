package org.mingora.launcher.gameinstall

import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.sink
import kotlinx.io.buffered
import org.mingora.launcher.gameinstall.task.GameInstallTask

enum class GameAudioLanguage(val code: String, val readableName: String) {
    Chinese("zh-cn", "Chinese"),
    English("en-us", "English(us)"),
    Japanese("ja-jp", "Japanese"),
    Korean("ko-kr", "Korean");

    companion object {
        internal fun setAudioLanguage(task: GameInstallTask) {
            val config = task.gameConfig
            val scanFile = task.installPath.resolve(config.audioPkgScanDir)
            scanFile.parent()?.also { if (!it.exists()) it.createDirectories() }
            val selected = task.audioLanguage.readableName
            scanFile.sink().buffered().use { sink ->
                sink.write(selected.encodeToByteArray(), endIndex = selected.encodeToByteArray().size)
                sink.flush()
            }
        }
    }
}
