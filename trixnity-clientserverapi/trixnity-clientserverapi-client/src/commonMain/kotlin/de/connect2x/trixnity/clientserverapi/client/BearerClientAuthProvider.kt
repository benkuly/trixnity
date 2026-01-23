package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.lognity.api.logger.Logger
import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.decodeErrorResponse

private val log = Logger("de.connect2x.trixnity.clientserverapi.client.BearerMatrixAuthProvider")

abstract class BearerClientAuthProvider<T : BearerTokens>(
    private val store: MatrixClientAuthProviderDataStore,
    private val onLogout: suspend (LogoutInfo) -> Unit,
) : MatrixClientAuthProvider {
    private val isRefreshingToken = MutableStateFlow(false)
    private val refreshTokenMutex = Mutex()

    override fun isApplicable(auth: HttpAuthHeader): Boolean =
        if (auth.authScheme != AuthScheme.Bearer) {
            log.error { "OAuth 2.0 Provider is not applicable for scheme '${auth.authScheme}'" }
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

        val token = store.getAuthData<T>() ?: run {
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
        val oldTokens = store.getAuthData<T>() ?: return false
        refreshTokenMutex.withLock {
            try {
                isRefreshingToken.value = true

                if (oldTokens != store.getAuthData<T>()) return true

                // Call onLogout when refresh token is not available
                val refreshToken = oldTokens.refreshToken
                if (response.status == HttpStatusCode.Unauthorized) {
                    val errorResponse = Json.decodeErrorResponse(response.bodyAsText())
                    when (errorResponse) {
                        is ErrorResponse.UnknownToken -> {
                            if (refreshToken == null) {
                                log.info { "no refresh token present, therefore call onLogout (unknown token)" }
                                onLogout(LogoutInfo(errorResponse.softLogout, false))
                            } else {
                                log.trace { "start refresh tokens oldTokens=$oldTokens" }
                                val newTokens = refreshTokens(oldTokens, response.call.client)
                                store.setAuthData(newTokens)
                                log.debug { "finish refresh tokens oldTokens=$oldTokens, newTokens=$newTokens" }
                                return true
                            }
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

    abstract suspend fun refreshTokens(bearerTokens: T, httpClient: HttpClient): T
}

interface BearerTokens : MatrixClientAuthProviderData {
    val accessToken: String
    val refreshToken: String?
}