package org.mingora.launcher.di

import io.ktor.client.HttpClient
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.mingora.launcher.core.util.FileDownloader
import org.mingora.launcher.core.util.httpClient
import org.mingora.launcher.hyp.HYPClient
import org.mingora.launcher.wine.WineInfEditor
import org.mingora.launcher.wine.WineInstaller

@OptIn(ExperimentalSerializationApi::class)
internal val commonModule = module {
    single<HttpClient> { httpClient() }
    single { Json { ignoreUnknownKeys = true } }
    single { createDataStore() }
    single { ProtoBuf {  } }
    single { FileDownloader(get()) }
}

internal val applicationModule = module {
    single { WineInfEditor() }
    single { WineInstaller(get(), get()) }
    single { HYPClient(get()) }
}

internal fun startKoinApp() {
    startKoin {
        modules(commonModule, applicationModule)
    }
}