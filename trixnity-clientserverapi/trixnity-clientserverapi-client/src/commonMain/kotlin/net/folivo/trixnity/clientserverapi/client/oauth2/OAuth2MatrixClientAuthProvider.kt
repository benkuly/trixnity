package net.folivo.trixnity.clientserverapi.client.oauth2

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.client.*
import net.folivo.trixnity.clientserverapi.model.authentication.TokenTypeHint
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.PromptValue
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ResponseMode
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import net.folivo.trixnity.core.AuthRequired
import okio.ByteString.Companion.toByteString
import kotlin.reflect.KClass

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.oauth2.OAuth2MatrixAuthProvider")

object OAuth2MatrixClientAuthProviderFactory : MatrixClientAuthProviderFactory {
    override val id: String = "OAuth2MatrixClientAuthProvider" // never change!
    override val supports: KClass<out MatrixClientAuthProviderData> = OAuth2MatrixClientAuthProviderData::class

    override suspend fun create(
        baseUrl: Url,
        store: MatrixClientAuthProviderStore,
        initialData: MatrixClientAuthProviderData?,
        onLogout: suspend (LogoutInfo) -> Unit,
        httpClientEngine: HttpClientEngine?,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)?
    ): MatrixClientAuthProvider<*> {
        if (initialData != null) {
            require(initialData is OAuth2MatrixClientAuthProviderData) { "initialData must be of type OAuth2MatrixClientAuthProviderData" }
            store.setAuthData(initialData)
        }
        return OAuth2MatrixClientAuthProvider(
            baseUrl = baseUrl,
            store = store,
            onLogout = onLogout,
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig
        )
    }
}

class OAuth2MatrixClientAuthProvider(
    private val baseUrl: Url,
    private val store: MatrixClientAuthProviderStore,
    private val onLogout: suspend (LogoutInfo) -> Unit,
    private val httpClientEngine: HttpClientEngine?,
    private val httpClientConfig: (HttpClientConfig<*>.() -> Unit)?,
) : BearerClientAuthProvider<OAuth2MatrixClientAuthProviderData>(
    store = store,
    dataSerializer = OAuth2MatrixClientAuthProviderData.serializer(),
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
    clientId: String,
    accessToken: String,
    accessTokenExpiresInS: Long? = null,
    refreshToken: String? = null,
    scope: Set<String>? = null,
): OAuth2MatrixClientAuthProviderData = OAuth2MatrixClientAuthProviderData(
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
    initialClientState: OAuth2LoginFlow.AuthenticateData.ClientState? = null,
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
    initialClientState = initialClientState,
    httpClientEngine = httpClientEngine,
    httpClientConfig = httpClientConfig
)

@Serializable
data class OAuth2MatrixClientAuthProviderData(
    override val accessToken: String,
    val accessTokenExpiresInS: Long?,
    override val refreshToken: String?,
    val clientId: String,
    val scope: Set<String>?,
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
}