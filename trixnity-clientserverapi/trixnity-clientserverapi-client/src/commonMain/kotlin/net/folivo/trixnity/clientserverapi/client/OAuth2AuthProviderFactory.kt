package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.decodeErrorResponse
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.OAuth2ProviderMetadata
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses.OAuth2ErrorResponse
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses.OAuth2TokenResponse

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.OAuth2MatrixAuthProvider")

private fun List<Pair<String, Any?>>.formUrlEncode() =
    filter { (_, value) -> value != null }.map { it.first to it.second.toString() }.formUrlEncode()

fun MatrixAuthProvider.Companion.oauth2(
    bearerTokensStore: BearerTokensStore,
    providerMetadata: OAuth2ProviderMetadata,
    clientId: String,
    onLogout: suspend (LogoutInfo) -> Unit = {}
) = OAuth2AuthProvider(bearerTokensStore, onLogout, providerMetadata, clientId)

fun MatrixAuthProvider.Companion.oauth2InMemory(
    accessToken: String? = null,
    refreshToken: String? = null,
    providerMetadata: OAuth2ProviderMetadata,
    clientId: String,
    onLogout: suspend (LogoutInfo) -> Unit = {},
) = oauth2(
    bearerTokensStore = BearerTokensStore.InMemory(
        accessToken?.let {
            BearerTokens(
                accessToken = it,
                refreshToken = refreshToken,
            )
        },
    ),
    providerMetadata = providerMetadata,
    clientId = clientId,
    onLogout = onLogout
)

class OAuth2AuthProvider(
    private val bearerTokensStore: BearerTokensStore,
    private val onLogout: suspend (LogoutInfo) -> Unit,
    private val providerMetadata: OAuth2ProviderMetadata,
    private val clientId: String
) : MatrixAuthProvider {
    private val isRefreshingToken = MutableStateFlow(false)
    private val refreshTokenMutex = Mutex()

    override fun isApplicable(auth: HttpAuthHeader): Boolean =
        if (auth.authScheme != AuthScheme.Bearer) {
            log.error { "Bearer Auth Provider is not applicable for $auth" }
            false
        } else true

    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        if (request.attributes.getOrNull(AuthCircuitBreaker) != null) {
            log.trace { "addRequestHeaders: skip due to AuthCircuitBreaker" }
            return
        }

        isRefreshingToken.first {
            if (it) log.trace { "addRequestHeaders: wait for refreshing to finish" }
            it.not()
        }

        val token = bearerTokensStore.getBearerTokens() ?: run {
            log.warn { "no bearer tokens found even after waiting for refresh" }
            return
        }

        request.headers {
            val tokenValue = "Bearer ${token.accessToken}"
            if (contains(HttpHeaders.Authorization)) {
                remove(HttpHeaders.Authorization)
            }
            append(HttpHeaders.Authorization, tokenValue)
        }
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        val oldTokens = bearerTokensStore.getBearerTokens() ?: return false
        refreshTokenMutex.withLock {
            try {
                val newOldTokens = bearerTokensStore.getBearerTokens()
                if (oldTokens != newOldTokens) return true

                // Call onLogout when refresh token is not available
                val refreshToken = oldTokens.refreshToken
                if (refreshToken == null) {
                    if (response.status == HttpStatusCode.Unauthorized) {
                        val errorResponse = Json.decodeErrorResponse(response.bodyAsText())
                        when (errorResponse) {
                            is ErrorResponse.UnknownToken -> {
                                log.info { "no refresh token present, therefore call onLogout (unknown token)" }
                                onLogout(LogoutInfo(errorResponse.softLogout, false))
                            }

                            is ErrorResponse.UserLocked -> {
                                log.info { "no refresh token present, therefore call onLogout (user locked)" }
                                onLogout(LogoutInfo(errorResponse.softLogout, true))
                            }

                            else -> {}
                        }
                        throw MatrixServerException(response.status, errorResponse, null)
                    }
                    return false
                }

                // Refresh token
                isRefreshingToken.value = true
                log.trace { "start refresh tokens oldTokens=$oldTokens, newOldTokens=$newOldTokens" }

                val refreshResponse = response.call.client.post(providerMetadata.tokenEndpoint) {
                    attributes.put(AuthCircuitBreaker, Unit)
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        listOf(
                            "grant_type" to GrantType.RefreshToken,
                            "refresh_token" to refreshToken,
                            "client_id" to clientId
                        ).formUrlEncode()
                    )
                }

                if (refreshResponse.status != HttpStatusCode.OK) {
                    val errorResponse = refreshResponse.body<OAuth2ErrorResponse>()
                    // TODO: Throw exception
                }

                val response = refreshResponse.body<OAuth2TokenResponse>()
                val newTokens = BearerTokens(response.accessToken, response.refreshToken ?: refreshToken)
                bearerTokensStore.setBearerTokens(newTokens)
                log.debug { "finish refresh tokens oldTokens=$oldTokens, newTokens=$newTokens" }
                return true
            } finally {
                isRefreshingToken.value = false
            }
        }
    }

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean =
        when (request.attributes.getOrNull(AuthRequired.attributeKey)) {
            AuthRequired.YES -> true
            AuthRequired.OPTIONAL -> true
            AuthRequired.NO -> false
            AuthRequired.NEVER -> false
            else -> false
        }
}
