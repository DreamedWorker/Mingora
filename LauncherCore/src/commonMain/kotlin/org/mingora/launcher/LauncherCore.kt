package org.mingora.launcher

import org.mingora.launcher.core.HYPLauncherId
import org.mingora.launcher.core.preference.IS_FIRST_LAUNCH
import org.mingora.launcher.core.preference.LAST_OPENED_GAME
import org.mingora.launcher.core.preference.LauncherPreference
import org.mingora.launcher.core.preference.MAINLY_LAUNCHER
import org.mingora.launcher.di.startKoinApp

fun startupLib() {
    Consts.makeDir()
    startKoinApp()
}

suspend fun isFirstLaunch(): Boolean {
    return LauncherPreference.getOrDefault(IS_FIRST_LAUNCH, true)
}

suspend fun wizardCompleted() {
    LauncherPreference.setValue(IS_FIRST_LAUNCH, false)
}

suspend fun getMainlyUsedLauncher(): HYPLauncherId {
    val id = LauncherPreference.getOrDefault(MAINLY_LAUNCHER, "jGHBHlcOq1")
    return HYPLauncherId.convert(id)
}

suspend fun getLastOpenedGame(): String? {
    val gameId = LauncherPreference.getOrDefault(LAST_OPENED_GAME, "")
    return gameId.ifBlank { null }
}

suspend fun setLastOpenedGame(gameId: String) {
    LauncherPreference.setValue(LAST_OPENED_GAME, gameId)
}