package net.folivo.trixnity.api.client

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*

val PlatformUserAgentPlugin = createClientPlugin("PlatformUserAgent") {
    this.onRequest { request, _ ->
        if (platformUserAgent != null && !request.headers.contains(HttpHeaders.UserAgent)) {
            request.header(HttpHeaders.UserAgent, platformUserAgent)
        }
    }
}