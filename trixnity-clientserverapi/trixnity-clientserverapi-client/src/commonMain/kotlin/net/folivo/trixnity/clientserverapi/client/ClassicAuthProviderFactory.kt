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
import net.folivo.trixnity.clientserverapi.model.authentication.Refresh
import net.folivo.trixnity.core.AuthRequired

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.ClassicMatrixAuthProvider")

fun MatrixAuthProvider.Companion.classic(
    bearerTokensStore: ClassicMatrixAuthProvider.BearerTokensStore,
) = ClassicMatrixAuthProvider(bearerTokensStore)

fun MatrixAuthProvider.Companion.classicInMemory(
    accessToken: String? = null,
    refreshToken: String? = null,
) = classic(
    bearerTokensStore = ClassicMatrixAuthProvider.BearerTokensStore.InMemory(
        accessToken?.let {
            ClassicMatrixAuthProvider.BearerTokens(
                accessToken = it,
                refreshToken = refreshToken,
            )
        },
    ),
)

class ClassicMatrixAuthProvider(
    private val bearerTokensStore: BearerTokensStore,
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
                if (oldTokens != bearerTokensStore.getBearerTokens()) return true

                log.debug { "refresh tokens oldTokens=$oldTokens" }
                isRefreshingToken.value = true

                val refreshToken = oldTokens.refreshToken ?: return false
                val refreshResponse =
                    response.call.client.post("/_matrix/client/v3/refresh") {
                        attributes.put(AuthCircuitBreaker, Unit)
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(Refresh.Request(refreshToken))
                    }.body<Refresh.Response>()
                val newTokens = BearerTokens(
                    accessToken = refreshResponse.accessToken,
                    refreshToken = refreshResponse.refreshToken ?: refreshToken,
                )
                bearerTokensStore.setBearerTokens(newTokens)
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
                    "accessToken=hash:${accessToken.hashCode()}, " +
                    "refreshToken=${refreshToken?.hashCode()?.let { "hash:$it" }}" +
                    ")"
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