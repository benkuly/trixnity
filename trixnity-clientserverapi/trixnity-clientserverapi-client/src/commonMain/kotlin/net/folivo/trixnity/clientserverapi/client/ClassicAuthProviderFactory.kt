package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.authentication.Refresh
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.decodeErrorResponse
import okio.ByteString.Companion.toByteString

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.ClassicMatrixAuthProvider")

fun MatrixAuthProvider.Companion.classic(
    bearerTokensStore: ClassicMatrixAuthProvider.BearerTokensStore,
    onLogout: suspend (LogoutInfo) -> Unit = {},
) = ClassicMatrixAuthProvider(bearerTokensStore, onLogout)

fun MatrixAuthProvider.Companion.classicInMemory(
    accessToken: String? = null,
    refreshToken: String? = null,
    onLogout: suspend (LogoutInfo) -> Unit = {},
) = classic(
    bearerTokensStore = ClassicMatrixAuthProvider.BearerTokensStore.InMemory(
        accessToken?.let {
            ClassicMatrixAuthProvider.BearerTokens(
                accessToken = it,
                refreshToken = refreshToken,
            )
        },
    ),
    onLogout = onLogout,
)

class ClassicMatrixAuthProvider(
    private val bearerTokensStore: BearerTokensStore,
    private val onLogout: suspend (LogoutInfo) -> Unit,
) : MatrixAuthProvider {
    private val isRefreshingToken = MutableStateFlow(false)

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

    private val refreshTokenMutex = Mutex()
    override suspend fun refreshToken(response: HttpResponse): Boolean {
        val oldTokens = bearerTokensStore.getBearerTokens() ?: return false
        refreshTokenMutex.withLock {
            try {
                val newOldTokens = bearerTokensStore.getBearerTokens()
                if (oldTokens != newOldTokens) return true

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
                isRefreshingToken.value = true
                log.trace { "start refresh tokens oldTokens=$oldTokens, newOldTokens=$newOldTokens" }
                val refreshResponse =
                    try {
                        response.call.client.post("/_matrix/client/v3/refresh") {
                            attributes.put(AuthCircuitBreaker, Unit)
                            contentType(ContentType.Application.Json)
                            accept(ContentType.Application.Json)
                            setBody(Refresh.Request(refreshToken))
                        }.body<Refresh.Response>()
                    } catch (matrixServerException: MatrixServerException) {
                        if (matrixServerException.statusCode == HttpStatusCode.Unauthorized)
                            when (val errorResponse = matrixServerException.errorResponse) {
                                is ErrorResponse.UnknownToken -> {
                                    log.info { "could not refresh token, therefore call onLogout (unknown token)" }
                                    onLogout(LogoutInfo(errorResponse.softLogout, false))
                                }

                                is ErrorResponse.UserLocked -> {
                                    log.info { "could not refresh token, therefore call onLogout (user locked)" }
                                    onLogout(LogoutInfo(errorResponse.softLogout, true))
                                }

                                else -> {}
                            }
                        throw matrixServerException
                    }
                val newTokens = BearerTokens(
                    accessToken = refreshResponse.accessToken,
                    refreshToken = refreshResponse.refreshToken ?: refreshToken,
                )
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

    @Serializable
    data class BearerTokens(
        val accessToken: String,
        val refreshToken: String?,
    ) {
        override fun toString(): String =
            "BearerTokens(" +
                    "accessToken=${accessToken.passwordHash()}, " +
                    "refreshToken=${refreshToken?.passwordHash()?.let { "hash:$it" }}" +
                    ")"

        private fun String.passwordHash() = "[hash:" + encodeToByteArray().toByteString().sha256().hex().take(6) + "]"
    }

    interface BearerTokensStore {
        suspend fun getBearerTokens(): BearerTokens?
        suspend fun setBearerTokens(bearerTokens: BearerTokens)

        class InMemory(initialValue: BearerTokens? = null) : BearerTokensStore {
            var bearerTokens = initialValue

            override suspend fun getBearerTokens(): BearerTokens? = bearerTokens

            override suspend fun setBearerTokens(bearerTokens: BearerTokens) {
                this.bearerTokens = bearerTokens
            }
        }
    }
}