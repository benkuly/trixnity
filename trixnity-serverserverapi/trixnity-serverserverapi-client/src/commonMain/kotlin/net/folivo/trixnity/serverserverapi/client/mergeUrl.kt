package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.request.*
import io.ktor.http.*

internal fun HttpRequestBuilder.mergeUrl(baseUrl: Url) {
    url {
        host = baseUrl.host
        port = baseUrl.port
        protocol = baseUrl.protocol
        encodedPath = baseUrl.encodedPath.removeSuffix("/") + url.encodedPath
    }
}