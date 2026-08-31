package org.mingora.launcher

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.resolve

internal object Consts {
    lateinit var userLanguage: String
    val appRootDir = FileKit.filesDir
    val appData = appRootDir.resolve("data")

    val downloadDir = appRootDir.resolve("downloads")
    val wineBinaryDir = appRootDir.resolve("wine")
    val winePrefix = appRootDir.resolve("prefix")

    const val WINE_URL = "https://github.com/yaagl/anime-game-wine/releases/download/wine-crossover-11.0-1-signed/wine-crossover-11.0-1-osx64-signed.tar.xz"
    const val DXMT_URL = "https://github.com/3Shain/dxmt/releases/download/v0.80/dxmt-v0.80-builtin.tar.gz"

    fun confirmLanguage(systemLanguage: String) {
        println("Confirming language: $systemLanguage")
        userLanguage = systemLanguage
    }

    fun makeDir() {
        if (!downloadDir.exists()) {
            downloadDir.createDirectories()
        }
        if (!wineBinaryDir.exists()) {
            wineBinaryDir.createDirectories()
        }
        if (!appData.exists()) {
            appData.createDirectories()
        }
    }
}