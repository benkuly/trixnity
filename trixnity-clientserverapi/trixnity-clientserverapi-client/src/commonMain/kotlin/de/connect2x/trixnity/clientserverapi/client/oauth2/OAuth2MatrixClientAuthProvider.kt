package de.connect2x.trixnity.clientserverapi.client.oauth2

import de.connect2x.lognity.api.logger.Logger
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.client.*
import de.connect2x.trixnity.clientserverapi.model.authentication.TokenTypeHint
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.PromptValue
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ResponseMode
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import de.connect2x.trixnity.core.AuthRequired
import okio.ByteString.Companion.toByteString

private val log = Logger("de.connect2x.trixnity.clientserverapi.client.oauth2.OAuth2MatrixAuthProvider")

class OAuth2MatrixClientAuthProvider(
    override val baseUrl: Url,
    private val store: MatrixClientAuthProviderDataStore,
    private val onLogout: suspend (LogoutInfo) -> Unit,
    private val httpClientEngine: HttpClientEngine?,
    private val httpClientConfig: (HttpClientConfig<*>.() -> Unit)?,
) : BearerClientAuthProvider<OAuth2MatrixClientAuthProviderData>(
    store = store,
    onLogout = onLogout
) {

    private val getServerMetadata = object {
        private var cachedValue: ServerMetadata? = null
        suspend operator fun invoke() = cachedValue ?: run {
            MatrixClientServerApiClientImpl(
                baseUrl = baseUrl,
                httpClientEngine = httpClientEngine,
                httpClientConfig = httpClientConfig
            ).use {
                it.authentication.getOAuth2ServerMetadata().getOrThrow()
            }.also {
                cachedValue = it
            }
        }
    }

    private suspend inline fun <T> withOAuth2Client(block: (OAuth2ApiClient) -> T): T =
        OAuth2ApiClient(getServerMetadata(), httpClientEngine, httpClientConfig).use { block(it) }

    override suspend fun logout(): Result<Unit> =
        store.getAuthData<OAuth2MatrixClientAuthProviderData>()?.let {
            log.debug { "Using OAuth2 for authentication, logging out by revoking OAuth2 token" }
            withOAuth2Client { oAuth2ApiClient ->
                oAuth2ApiClient.revokeToken(
                    token = it.refreshToken ?: it.accessToken,
                    tokenTypeHint = if (it.refreshToken != null) TokenTypeHint.RefreshToken else TokenTypeHint.AccessToken,
                    clientId = it.clientId
                )
            }
        } ?: Result.failure(IllegalStateException("No tokens stored"))

    override suspend fun refreshTokens(
        bearerTokens: OAuth2MatrixClientAuthProviderData,
        httpClient: HttpClient
    ): OAuth2MatrixClientAuthProviderData {
        val refreshToken = bearerTokens.refreshToken ?: return bearerTokens
        withOAuth2Client { oAuth2ApiClient ->
            oAuth2ApiClient.refreshToken(refreshToken, bearerTokens.clientId)
        }.fold(
            onSuccess = { refreshResponse ->
                return OAuth2MatrixClientAuthProviderData(
                    baseUrl = bearerTokens.baseUrl,
                    accessToken = refreshResponse.accessToken,
                    accessTokenExpiresInS = refreshResponse.expiresIn,
                    refreshToken = refreshResponse.refreshToken ?: refreshToken,
                    clientId = bearerTokens.clientId,
                    scope = refreshResponse.scope,
                )
            },
            onFailure = { errorResponse ->
                if (errorResponse is ClientRequestException) {
                    log.info { "could not refresh token, therefore call onLogout (unknown token)" }
                    onLogout(LogoutInfo(isSoft = true, isLocked = false))
                }
                throw errorResponse
            }
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

fun MatrixClientAuthProviderData.Companion.oAuth2(
    baseUrl: Url,
    clientId: String,
    accessToken: String,
    accessTokenExpiresInS: Long? = null,
    refreshToken: String? = null,
    scope: Set<Scope>? = null,
): OAuth2MatrixClientAuthProviderData = OAuth2MatrixClientAuthProviderData(
    baseUrl = baseUrl,
    accessToken = accessToken,
    accessTokenExpiresInS = accessTokenExpiresInS,
    refreshToken = refreshToken,
    clientId = clientId,
    scope = scope,
)

fun MatrixClientAuthProviderData.Companion.oAuth2Login(
    baseUrl: Url,
    applicationType: ApplicationType,
    clientUri: String,
    redirectUri: String,
    responseMode: ResponseMode = ResponseMode.Fragment,
    clientName: LocalizedField<String>? = null,
    logoUri: LocalizedField<String>? = null,
    policyUri: LocalizedField<String>? = null,
    tosUri: LocalizedField<String>? = null,
    promptValue: PromptValue? = null,
    initialState: OAuth2LoginFlow.AuthRequestData.State? = null,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
): OAuth2LoginFlow = OAuth2LoginFlowImpl(
    baseUrl = baseUrl,
    applicationType = applicationType,
    clientUri = clientUri,
    redirectUri = redirectUri,
    responseMode = responseMode,
    clientName = clientName,
    logoUri = logoUri,
    policyUri = policyUri,
    tosUri = tosUri,
    promptValue = promptValue,
    initialState = initialState,
    httpClientEngine = httpClientEngine,
    httpClientConfig = httpClientConfig
)

@Serializable
data class OAuth2MatrixClientAuthProviderData(
    override val baseUrl: Url,
    override val accessToken: String,
    val accessTokenExpiresInS: Long?,
    override val refreshToken: String?,
    val clientId: String,
    val scope: Set<Scope>?,
) : MatrixClientAuthProviderData, BearerTokens {
    override fun toString(): String =
        "OAuth2MatrixAuthProviderData(" +
                "accessToken=${accessToken.tokenHash()}, " +
                "accessTokenExpiresInMs=$accessTokenExpiresInS," +
                "refreshToken=${refreshToken?.tokenHash()}, " +
                "clientId=$clientId" +
                "scope=$scope" +
                ")"

    private fun String.tokenHash() = "<hash:" + encodeToByteArray().toByteString().sha256().hex().take(6) + ">"
    override fun createAuthProvider(
        store: MatrixClientAuthProviderDataStore,
        onLogout: suspend (LogoutInfo) -> Unit,
        httpClientEngine: HttpClientEngine?,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)?
    ): MatrixClientAuthProvider = OAuth2MatrixClientAuthProvider(
        baseUrl = baseUrl,
        store = store,
        onLogout = onLogout,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    )
}