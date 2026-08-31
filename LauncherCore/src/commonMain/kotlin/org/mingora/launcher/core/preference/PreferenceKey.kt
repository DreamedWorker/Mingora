package org.mingora.launcher.core.preference

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
val MAINLY_LAUNCHER = stringPreferencesKey("main_launcher")
val LAST_OPENED_GAME = stringPreferencesKey("last_opened_game")