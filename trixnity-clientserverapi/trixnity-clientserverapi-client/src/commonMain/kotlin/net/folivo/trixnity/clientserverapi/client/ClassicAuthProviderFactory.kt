package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.Refresh
import net.folivo.trixnity.core.AuthRequired

private val log = KotlinLogging.logger { }

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
    private val refreshTokensDeferred = MutableStateFlow<Lazy<Deferred<Boolean>>?>(null)

    override fun isApplicable(auth: HttpAuthHeader): Boolean =
        if (auth.authScheme != AuthScheme.Bearer) {
            log.trace { "Bearer Auth Provider is not applicable for $auth" }
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
        refreshTokensDeferred
            .value?.value
            ?.also { log.trace { "addRequestHeaders: wait for refreshing to finish" } }
            ?.join()
        val token = bearerTokensStore.getBearerTokens() ?: run {
            log.trace { "addRequestHeaders: no bearer tokens found" }
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

    override suspend fun refreshToken(response: HttpResponse): Boolean = coroutineScope {
        val lazyRefreshToken = lazy {
            async {
                try {
                    val oldTokens = bearerTokensStore.getBearerTokens() ?: return@async false
                    log.trace { "refreshTokens: oldTokens=$oldTokens" }

                    val refreshToken = oldTokens.refreshToken ?: return@async false
                    val refreshResponse =
                        response.call.client.post("/_matrix/client/v3/refresh") {
                            attributes.put(AuthCircuitBreaker, Unit)
                            contentType(ContentType.Application.Json)
                            accept(ContentType.Application.Json)
                            setBody(Refresh.Request(refreshToken))
                        }.body<Refresh.Response>()
                    val newTokens = BearerTokens(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken,
                    )
                    bearerTokensStore.setBearerTokens(newTokens)
                    return@async true
                } finally {
                    refreshTokensDeferred.value = null
                }
            }
        }
        refreshTokensDeferred.updateAndGet { it ?: lazyRefreshToken }?.value?.await()
            ?: throw IllegalStateException("should never be null")
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
            "BearerTokens(accessToken=***, refreshToken=${refreshToken?.let { "***" }})"
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