package org.mingora.launcher.core.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object LauncherPreference : KoinComponent {
    private val dataStore: DataStore<Preferences> by inject()

    suspend fun <T> getOrDefault(preferenceKey: Preferences.Key<T>, default: T): T {
        return dataStore.data.map { preferences ->
            preferences[preferenceKey] ?: default
        }.first()
    }

    suspend fun <T> setValue(preferenceKey: Preferences.Key<T>, value: T) {
        dataStore.updateData {
            it.toMutablePreferences().also { preferences ->
                preferences[preferenceKey] = value
            }
        }
    }
}