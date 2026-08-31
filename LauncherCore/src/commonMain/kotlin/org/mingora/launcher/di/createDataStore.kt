package org.mingora.launcher.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal expect fun createDataStore(): DataStore<Preferences>

internal const val dataStoreFileName = "app.preferences_pb"