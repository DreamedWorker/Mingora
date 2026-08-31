package org.mingora.launcher.core.util

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect fun httpClient(): HttpClient

internal fun HttpClientConfig<*>.applyCommonConfig() {
    install(ContentNegotiation) {
        json(
            Json { ignoreUnknownKeys = true }
        )
    }
}