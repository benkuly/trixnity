package net.folivo.trixnity.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.api.client.defaultTrixnityHttpClientFactory
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.core.model.UserId

suspend fun UserId.serverDiscovery(
    httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClientFactory()
): Result<Url> = domain.serverDiscovery(httpClientFactory)

suspend fun String.serverDiscovery(
    httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClientFactory()
): Result<Url> = runCatching {
    val hostnameBaseUrl =
        when {
            startsWith("http://") || startsWith("https://") -> Url(this)
            else -> Url("https://$this")
        }
    return hostnameBaseUrl.serverDiscovery(httpClientFactory)
}

suspend fun Url.serverDiscovery(
    httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClientFactory()
): Result<Url> = kotlin.runCatching {
    require(protocol == URLProtocol.HTTP || protocol == URLProtocol.HTTPS) { "protocol must be http or https" }
    val hostnameBaseUrl = Url(URLBuilder(protocol, host, port))
    val discoveryBaseUrl = MatrixApiClient(httpClientFactory = {
        httpClientFactory {
            it()
            defaultRequest { url.takeFrom(hostnameBaseUrl) }
        }
    }).request(GetWellKnown)
        .map { Url(it.homeserver.baseUrl.removeSuffix("/")) }
        .getOrElse { this } // fallback when no .well-known exists
    MatrixApiClient(httpClientFactory = {
        httpClientFactory {
            it()
            defaultRequest { url.takeFrom(discoveryBaseUrl) }
        }
    }).request(GetVersions).getOrThrow()
    discoveryBaseUrl
}