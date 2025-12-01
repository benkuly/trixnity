package net.folivo.trixnity.clientserverapi.client.oauth2

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.PlatformUserAgentPlugin
import net.folivo.trixnity.clientserverapi.model.authentication.TokenTypeHint
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata

internal class OAuth2ApiClient(
    private val serverMetadata: ServerMetadata,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : AutoCloseable {
    private val finalHttpClientConfig: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        expectSuccess = true

        install(PlatformUserAgentPlugin)
        install(HttpCache) // cache server metadata

        httpClientConfig?.invoke(this)
    }
    private val baseClient: HttpClient =
        if (httpClientEngine == null) HttpClient(finalHttpClientConfig)
        else HttpClient(httpClientEngine, finalHttpClientConfig)

    suspend fun registerClient(metadata: ClientMetadata): Result<ClientRegistrationResponse> = runCatching {
        baseClient.post(serverMetadata.registrationEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(metadata)
        }.body<ClientRegistrationResponse>()
    }

    suspend fun getToken(
        clientId: String,
        redirectUri: String,
        code: String,
        codeVerifier: String
    ): Result<TokenResponse> = runCatching {
        baseClient.submitForm(
            url = serverMetadata.tokenEndpoint.toString(),
            formParameters = parameters {
                append("grant_type", GrantType.AuthorizationCode.value)
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", codeVerifier)
            }
        ).body<TokenResponse>()
    }

    suspend fun refreshToken(
        refreshToken: String,
        clientId: String?
    ): Result<TokenResponse> = runCatching {
        baseClient.submitForm(
            url = serverMetadata.tokenEndpoint.toString(),
            formParameters = parameters {
                append("grant_type", GrantType.RefreshToken.value)
                append("refresh_token", refreshToken)
                clientId?.let { append("client_id", it) }
            }
        ).body<TokenResponse>()
    }

    suspend fun revokeToken(
        token: String,
        tokenTypeHint: TokenTypeHint?,
        clientId: String?
    ): Result<Unit> = runCatching {
        baseClient.submitForm(
            url = serverMetadata.revocationEndpoint.toString(),
            formParameters = parameters {
                append("token", token)
                tokenTypeHint?.let { append("token_type_hint", it.value) }
                clientId?.let { append("client_id", it) }
            }
        )
    }

    override fun close() {
        baseClient.close()
    }
}