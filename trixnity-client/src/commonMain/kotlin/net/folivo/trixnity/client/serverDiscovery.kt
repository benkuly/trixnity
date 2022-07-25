package net.folivo.trixnity.client

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.core.model.UserId

suspend fun UserId.serverDiscovery(
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) }
): Result<Url> = domain.serverDiscovery(httpClientFactory)

suspend fun String.serverDiscovery(
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) }
): Result<Url> = kotlin.runCatching {
    val hostWithPort = Url(this).hostWithPort
    val hostnameBaseUrl =
        if (this.startsWith("http://")) Url("http://$hostWithPort")
        else Url("https://$hostWithPort")
    val discoveryBaseUrl = MatrixApiClient(httpClientFactory = {
        httpClientFactory {
            it()
            defaultRequest { url.takeFrom(hostnameBaseUrl) }
        }
    }).request(GetWellKnown)
        .map { Url(it.homeserver.baseUrl.removeSuffix("/")) }.getOrThrow()
    MatrixApiClient(httpClientFactory = {
        httpClientFactory {
            it()
            defaultRequest { url.takeFrom(discoveryBaseUrl) }
        }
    }).request(GetVersions).getOrThrow()
    discoveryBaseUrl
}