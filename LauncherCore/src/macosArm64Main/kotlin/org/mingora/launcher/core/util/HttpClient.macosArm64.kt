package org.mingora.launcher.core.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun httpClient(): HttpClient {
    return HttpClient(Darwin) {
        applyCommonConfig()
    }
}