package net.folivo.trixnity.api.client

import io.ktor.client.*
import io.ktor.client.plugins.*

fun defaultTrixnityHttpClientFactory(
    userAgent: String? = platformUserAgent,
    config: HttpClientConfig<*>.() -> Unit = {},
): ((HttpClientConfig<*>.() -> Unit) -> HttpClient) = { baseConfig ->
    HttpClient {
        baseConfig()
        if (userAgent != null)
            install(UserAgent) {
                agent = userAgent
            }
        config()
    }
}