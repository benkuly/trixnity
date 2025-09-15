package net.folivo.trixnity.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.OAuth2ProviderMetadata
import net.folivo.trixnity.core.model.UserId

suspend fun UserId.serverDiscovery(
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<Url> = domain.serverDiscovery(httpClientEngine, httpClientConfig)

suspend fun String.serverDiscoveryWithOAuth2(
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<Pair<Url, OAuth2ProviderMetadata?>> = kotlin.runCatching {
    val hostnameBaseUrl =
        when {
            startsWith("http://") || startsWith("https://") -> Url(this)
            else -> Url("https://$this")
        }
    return hostnameBaseUrl.serverDiscoveryWithOAuth2(httpClientEngine, httpClientConfig)
}

suspend fun String.serverDiscovery(
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<Url> = runCatching {
    val hostnameBaseUrl =
        when {
            startsWith("http://") || startsWith("https://") -> Url(this)
            else -> Url("https://$this")
        }
    return hostnameBaseUrl.serverDiscovery(httpClientEngine, httpClientConfig)
}

suspend fun Url.serverDiscoveryWithOAuth2(
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<Pair<Url, OAuth2ProviderMetadata?>> = kotlin.runCatching {
    require(protocol == URLProtocol.HTTP || protocol == URLProtocol.HTTPS) { "protocol must be http or https" }
    val hostnameBaseUrl = Url(URLBuilder(protocol, host, port))
    val discoveryBaseUrl = MatrixClientServerApiClientImpl(
        hostnameBaseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    ).use {
        it.discovery.getWellKnown()
            .map { Url(it.homeserver.baseUrl.removeSuffix("/")) }
            .getOrElse { this } // fallback when no .well-known exists
    }
    val oauth2Metadata = MatrixClientServerApiClientImpl(
        discoveryBaseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    ).use {
        it.server.getVersions().getOrThrow()
        it.authentication.getOAuth2ProviderMetadata().getOrNull()
    }
    Pair(discoveryBaseUrl, oauth2Metadata)
}

suspend fun Url.serverDiscovery(
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<Url> = kotlin.runCatching {
    require(protocol == URLProtocol.HTTP || protocol == URLProtocol.HTTPS) { "protocol must be http or https" }
    val hostnameBaseUrl = Url(URLBuilder(protocol, host, port))
    val discoveryBaseUrl = MatrixClientServerApiClientImpl(
        hostnameBaseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    ).use {
        it.discovery.getWellKnown()
            .map { Url(it.homeserver.baseUrl.removeSuffix("/")) }
            .getOrElse { this } // fallback when no .well-known exists
    }
    MatrixClientServerApiClientImpl(
        discoveryBaseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    ).use {
        it.server.getVersions().getOrThrow()
    }
    discoveryBaseUrl
}