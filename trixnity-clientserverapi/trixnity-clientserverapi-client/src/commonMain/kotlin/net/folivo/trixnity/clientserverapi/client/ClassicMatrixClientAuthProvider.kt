package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.authentication.Refresh
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import okio.ByteString.Companion.toByteString

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.ClassicMatrixAuthProvider")

class ClassicMatrixClientAuthProvider(
    override val baseUrl: Url,
    store: MatrixClientAuthProviderDataStore,
    private val onLogout: suspend (LogoutInfo) -> Unit,
) : BearerClientAuthProvider<ClassicMatrixClientAuthProviderData>(
    store = store,
    onLogout = onLogout
) {
    override suspend fun refreshTokens(
        bearerTokens: ClassicMatrixClientAuthProviderData,
        httpClient: HttpClient
    ): ClassicMatrixClientAuthProviderData {
        val refreshToken = bearerTokens.refreshToken ?: return bearerTokens
        val refreshResponse =
            try {
                httpClient.post("/_matrix/client/v3/refresh") {
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

                        else -> {}
                    }
                throw matrixServerException
            }
        return ClassicMatrixClientAuthProviderData(
            baseUrl = bearerTokens.baseUrl,
            accessToken = refreshResponse.accessToken,
            accessTokenExpiresInMs = refreshResponse.accessTokenExpiresInMs,
            refreshToken = refreshResponse.refreshToken ?: refreshToken,
        )
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


@Serializable
data class ClassicMatrixClientAuthProviderData(
    override val baseUrl: Url,
    override val accessToken: String,
    val accessTokenExpiresInMs: Long?,
    override val refreshToken: String?,
) : MatrixClientAuthProviderData, BearerTokens {
    override fun toString(): String =
        "ClassicMatrixAuthProviderData(" +
                "accessToken=${accessToken.tokenHash()}, " +
                "accessTokenExpiresInMs=$accessTokenExpiresInMs, " +
                "refreshToken=${refreshToken?.tokenHash()}, " +
                ")"

    private fun String.tokenHash() = "<hash:" + encodeToByteArray().toByteString().sha256().hex().take(6) + ">"
    override fun createAuthProvider(
        store: MatrixClientAuthProviderDataStore,
        onLogout: suspend (LogoutInfo) -> Unit,
        httpClientEngine: HttpClientEngine?,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)?
    ): MatrixClientAuthProvider = ClassicMatrixClientAuthProvider(
        baseUrl = baseUrl,
        store = store,
        onLogout = onLogout
    )
}

fun MatrixClientAuthProviderData.Companion.classic(
    baseUrl: Url,
    accessToken: String,
    accessTokenExpiresInMs: Long? = null,
    refreshToken: String? = null,
): ClassicMatrixClientAuthProviderData = ClassicMatrixClientAuthProviderData(
    baseUrl = baseUrl,
    accessToken = accessToken,
    accessTokenExpiresInMs = accessTokenExpiresInMs,
    refreshToken = refreshToken
)

suspend fun MatrixClientAuthProviderData.Companion.classicLogin(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String? = null,
    token: String? = null,
    loginType: LoginType = LoginType.Password,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    refreshToken: Boolean? = null,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<ClassicMatrixClientAuthProviderData> =
    classicLoginWith(
        baseUrl = baseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
    ) { api ->
        api.authentication.login(
            identifier = identifier,
            password = password,
            token = token,
            type = loginType,
            deviceId = deviceId,
            initialDeviceDisplayName = initialDeviceDisplayName,
            refreshToken = refreshToken
        ).getOrThrow().let { login ->
            ClassicMatrixClientAuthProviderData(
                baseUrl = baseUrl,
                accessToken = login.accessToken,
                accessTokenExpiresInMs = login.accessTokenExpiresInMs,
                refreshToken = login.refreshToken,
            )
        }
    }

suspend fun MatrixClientAuthProviderData.Companion.classicLoginWithPassword(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    password: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    refreshToken: Boolean? = null,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<ClassicMatrixClientAuthProviderData> =
    classicLogin(
        baseUrl = baseUrl,
        identifier = identifier,
        password = password,
        token = null,
        loginType = LoginType.Password,
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        refreshToken = refreshToken,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
    )

suspend fun MatrixClientAuthProviderData.Companion.classicLoginWithToken(
    baseUrl: Url,
    identifier: IdentifierType? = null,
    token: String,
    deviceId: String? = null,
    initialDeviceDisplayName: String? = null,
    refreshToken: Boolean? = null,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): Result<ClassicMatrixClientAuthProviderData> =
    classicLogin(
        baseUrl = baseUrl,
        identifier = identifier,
        password = null,
        token = token,
        loginType = LoginType.Token(),
        deviceId = deviceId,
        initialDeviceDisplayName = initialDeviceDisplayName,
        refreshToken = refreshToken,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
    )

suspend fun MatrixClientAuthProviderData.Companion.classicLoginWith(
    baseUrl: Url,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    with: suspend (MatrixClientServerApiClient) -> ClassicMatrixClientAuthProviderData,
): Result<ClassicMatrixClientAuthProviderData> = kotlin.runCatching {
    MatrixClientServerApiClientImpl(
        baseUrl = baseUrl,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
    ).use { api ->
        with(api)
    }
}