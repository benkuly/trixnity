package de.connect2x.trixnity.clientserverapi.client.oauth2

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.*
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.core.Sha256
import de.connect2x.trixnity.utils.nextString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


/**
 * Represents the flow for handling OAuth 2.0 login operations. This interface provides methods to facilitate
 * the redirection and callback required during the OAuth 2.0 authorization process.
 */
interface OAuth2LoginFlow {
    /**
     * Initiates the OAuth 2.0 authentication process.
     * This method generates an authentication request containing a URL and associated metadata
     * necessary when the app has been killed during the authentication process.
     */
    suspend fun createAuthRequest(): Result<AuthRequestData>

    /**
     * Represents the data required for initiating an OAuth 2.0 authentication process.
     * It includes the URL for the authentication request and serializable state information to continue authentication after the app has been killed.
     */
    data class AuthRequestData(
        val url: String,
        val state: State,
    ) {
        /**
         * Represents the state information used during an OAuth 2.0 authentication flow.
         */
        @Serializable
        data class State(
            val clientId: String,
            val state: String,
            val codeVerifier: String,
        )
    }

    /**
     * Handles the callback from the OAuth2 authorization server.
     */
    suspend fun onCallback(callbackUri: Url): Result<OAuth2MatrixClientAuthProviderData>
}

class OAuth2LoginFlowImpl(
    private val baseUrl: Url,
    private val applicationType: ApplicationType,
    private val clientUri: String,
    private val redirectUri: String,
    private val responseMode: ResponseMode = ResponseMode.Fragment,
    private val clientName: LocalizedField<String>? = null,
    private val logoUri: LocalizedField<String>? = null,
    private val policyUri: LocalizedField<String>? = null,
    private val tosUri: LocalizedField<String>? = null,
    private val promptValue: PromptValue? = null,
    initialState: OAuth2LoginFlow.AuthRequestData.State? = null,
    private val httpClientEngine: HttpClientEngine? = null,
    private val httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : OAuth2LoginFlow {
    private var clientId: String? = initialState?.clientId
    private val deviceId = SecureRandom.nextString(10, alphabet = ('a'..'z') + ('A'..'Z'))

    @OptIn(ExperimentalUuidApi::class)
    private val state = initialState?.state
        ?: Uuid.fromByteArray(SecureRandom.nextBytes(Uuid.SIZE_BYTES)).toHexDashString()
    private val codeVerifier = initialState?.codeVerifier
        ?: SecureRandom.nextString(64)
    private val codeChallenge = Sha256().use {
        it.update(codeVerifier.encodeToByteArray())
        Base64.UrlSafe.withPadding(ABSENT).encode(it.digest())
    }

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

    override suspend fun createAuthRequest(): Result<OAuth2LoginFlow.AuthRequestData> = runCatching {
        OAuth2ApiClient(
            serverMetadata = getServerMetadata(),
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig,
        ).use {
            val clientMetadata = it.registerClient(
                ClientMetadata(
                    applicationType = applicationType,
                    clientUri = clientUri,
                    redirectUris = setOf(redirectUri),
                    clientName = clientName,
                    logoUri = logoUri,
                    policyUri = policyUri,
                    tosUri = tosUri,
                    grantTypes = setOf(GrantType.AuthorizationCode, GrantType.RefreshToken),
                    responseTypes = setOf(ResponseType.Code),
                    tokenEndpointAuthMethod = TokenEndpointAuthMethod.None
                )
            ).getOrThrow()
            val authenticationRequestUrl = URLBuilder(getServerMetadata().authorizationEndpoint).apply {
                parameters.append("response_type", ResponseType.Code.value)
                parameters.append("client_id", clientMetadata.clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append(
                    "scope", listOf(
                        Scope.MatrixClientApi.value,
                        Scope.MatrixClientDevice(deviceId).value,
                    ).joinToString(" ")
                )
                parameters.append("state", state)
                parameters.append("response_mode", responseMode.value)
                parameters.append("code_challenge", codeChallenge)
                parameters.append("code_challenge_method", CodeChallengeMethod.S256.value)
                promptValue?.let { parameters.append("prompt", it.value) }
            }.build().toString()
            clientId = clientMetadata.clientId
            OAuth2LoginFlow.AuthRequestData(
                url = authenticationRequestUrl,
                state = OAuth2LoginFlow.AuthRequestData.State(
                    clientId = clientMetadata.clientId,
                    state = state,
                    codeVerifier = codeVerifier,
                )
            )
        }
    }

    override suspend fun onCallback(callbackUri: Url): Result<OAuth2MatrixClientAuthProviderData> = runCatching {
        val parameters = when (responseMode) {
            ResponseMode.Query -> callbackUri.parameters
            ResponseMode.Fragment -> parseQueryString(callbackUri.fragment)
            is ResponseMode.Unknown -> throw IllegalStateException("unsupported response mode")
        }
        val callbackCode = requireNotNull(parameters["code"]) { "code is missing" }
        val callbackState = requireNotNull(parameters["state"]) { "state is missing" }
        val clientId =
            requireNotNull(clientId) { "clientId is missing (need to call authenticate first or set initialState)" }

        check(callbackState == state) { "callback state did not match" }

        val tokenResponse = OAuth2ApiClient(
            serverMetadata = getServerMetadata(),
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig,
        ).use {
            it.getToken(
                clientId = clientId,
                redirectUri = redirectUri,
                code = callbackCode,
                codeVerifier = codeVerifier,
            )
        }.getOrThrow()

        OAuth2MatrixClientAuthProviderData(
            baseUrl = baseUrl,
            accessToken = tokenResponse.accessToken,
            accessTokenExpiresInS = tokenResponse.expiresIn,
            refreshToken = tokenResponse.refreshToken,
            clientId = clientId,
            scope = tokenResponse.scope,
        )
    }
}