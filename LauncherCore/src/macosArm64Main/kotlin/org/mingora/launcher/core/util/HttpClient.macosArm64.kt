package org.mingora.launcher.core.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun httpClient(): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureSession {
                // 大型 Sophon chunk 可能持续数分钟；NSURLSession 的默认请求空闲
                // 超时过短时会在仍有大量连接竞争时频繁触发 SocketTimeoutException。
                timeoutIntervalForRequest = 300.0
                timeoutIntervalForResource = 24.0 * 60.0 * 60.0
                waitsForConnectivity = true
                HTTPMaximumConnectionsPerHost = 6L
            }
        }
        applyCommonConfig()
    }
}
