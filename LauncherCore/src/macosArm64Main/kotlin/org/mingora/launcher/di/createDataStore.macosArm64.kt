package org.mingora.launcher.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import io.github.vinceglb.filekit.resolve
import okio.FileSystem
import okio.Path.Companion.toPath
import org.mingora.launcher.Consts

internal actual fun createDataStore(): DataStore<Preferences> {
    val file = Consts.appData.resolve(dataStoreFileName)

    return DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = PreferencesSerializer,
            producePath = { file.nsUrl.path!!.toPath() }
        )
    )
}