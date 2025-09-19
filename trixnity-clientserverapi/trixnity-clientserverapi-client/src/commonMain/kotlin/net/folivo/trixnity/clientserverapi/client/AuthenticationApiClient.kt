package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.folivo.trixnity.api.client.disableMatrixErrorHandling
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.CodeChallengeMethod
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.OAuth2ProviderMetadata
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ResponseMode
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ResponseType
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.TokenTypeHint
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.client.OAuth2ClientMetadata
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.client.OAuth2ClientRegistrationResponse
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses.OAuth2ErrorException
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses.OAuth2TokenResponse
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.crypto.core.CodeChallenge
import net.folivo.trixnity.crypto.core.CodeVerifier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

interface AuthenticationApiClient {
    /**
     * @see [WhoAmI]
     */
    suspend fun whoAmI(asUserId: UserId? = null): Result<WhoAmI.Response>

    /**
     * @see [IsRegistrationTokenValid]
     */
    suspend fun isRegistrationTokenValid(
        token: String
    ): Result<Boolean>

    /**
     * @see [IsUsernameAvailable]
     */
    suspend fun isUsernameAvailable(
        username: String
    ): Result<Boolean>

    /**
     * @see [GetEmailRequestTokenForPassword]
     */
    suspend fun getEmailRequestTokenForPassword(
        request: GetEmailRequestTokenForPassword.Request
    ): Result<GetEmailRequestTokenForPassword.Response>

    /**
     * @see [GetEmailRequestTokenForRegistration]
     */
    suspend fun getEmailRequestTokenForRegistration(
        request: GetEmailRequestTokenForRegistration.Request
    ): Result<GetEmailRequestTokenForRegistration.Response>

    /**
     * @see [GetMsisdnRequestTokenForPassword]
     */
    suspend fun getMsisdnRequestTokenForPassword(
        request: GetMsisdnRequestTokenForPassword.Request
    ): Result<GetMsisdnRequestTokenForPassword.Response>

    /**
     * @see [GetMsisdnRequestTokenForRegistration]
     */
    suspend fun getMsisdnRequestTokenForRegistration(
        request: GetMsisdnRequestTokenForRegistration.Request
    ): Result<GetMsisdnRequestTokenForRegistration.Response>

    /**
     * @see [Register]
     */
    suspend fun register(
        username: String? = null,
        password: String? = null,
        accountType: AccountType? = null,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null,
        inhibitLogin: Boolean? = null,
        refreshToken: Boolean? = null,
        isAppservice: Boolean = false,
    ): Result<UIA<Register.Response>>

    fun getSsoUrl(
        redirectUrl: String,
        idpId: String? = null,
    ): String

    /**
     * @see [GetLoginTypes]
     */
    suspend fun getLoginTypes(): Result<Set<LoginType>>

    /**
     * @see [OAuth2ProviderMetadata]
     */
    suspend fun getOAuth2ProviderMetadata(timeout: Duration = 15.seconds): Result<OAuth2ProviderMetadata>

    /**
     * @return The id of the registered client
     *
     * @see [OAuth2ProviderMetadata]
     */
    suspend fun registerOAuth2Client(metadata: OAuth2ClientMetadata): Result<OAuth2ClientRegistrationResponse>

    suspend fun revokeOAuth2Token(
        token: String,
        tokenTypeHint: TokenTypeHint? = null,
        clientId: String? = null
    ): Result<Unit>

    /**
     * @see [OAuth2TokenResponse]
     */
    suspend fun getOAuth2Token(
        code: String,
        redirectUrl: String,
        clientId: String,
        codeVerifier: CodeVerifier
    ): Result<OAuth2TokenResponse>

    fun isOAuth2User(): Boolean

    /**
     * @see [Login]
     */
    @Deprecated("use login with separated password and token")
    suspend fun login(
        identifier: IdentifierType? = null,
        passwordOrToken: String,
        type: LoginType = LoginType.Password,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null
    ): Result<Login.Response>

    /**
     * @see [Login]
     */
    suspend fun login(
        identifier: IdentifierType? = null,
        password: String? = null,
        token: String? = null,
        type: LoginType = LoginType.Password,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null,
        refreshToken: Boolean? = null
    ): Result<Login.Response>

    /**
     * @see [Logout]
     */
    suspend fun logout(asUserId: UserId? = null): Result<Unit>

    /**
     * @see [LogoutAll]
     */
    suspend fun logoutAll(asUserId: UserId? = null): Result<Unit>

    /**
     * @see [DeactivateAccount]
     */
    suspend fun deactivateAccount(
        identityServer: String? = null,
        erase: Boolean? = null,
        asUserId: UserId? = null
    ): Result<UIA<DeactivateAccount.Response>>

    /**
     * @see [ChangePassword]
     */
    suspend fun changePassword(
        newPassword: String,
        logoutDevices: Boolean = false
    ): Result<UIA<Unit>>

    /**
     * @see [GetThirdPartyIdentifiers]
     */
    suspend fun getThirdPartyIdentifiers(
        asUserId: UserId? = null,
    ): Result<Set<ThirdPartyIdentifier>>

    /**
     * @see [AddThirdPartyIdentifiers]
     */
    suspend fun addThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        asUserId: UserId? = null,
    ): Result<UIA<Unit>>

    /**
     * @see [BindThirdPartyIdentifiers]
     */
    suspend fun bindThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        idAccessToken: String,
        idServer: String,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see [DeleteThirdPartyIdentifiers]
     */
    suspend fun deleteThirdPartyIdentifiers(
        address: String,
        idServer: String? = null,
        medium: ThirdPartyIdentifier.Medium,
        asUserId: UserId? = null,
    ): Result<DeleteThirdPartyIdentifiers.Response>

    /**
     * @see [UnbindThirdPartyIdentifiers]
     */
    suspend fun unbindThirdPartyIdentifiers(
        address: String,
        idServer: String? = null,
        medium: ThirdPartyIdentifier.Medium,
        asUserId: UserId? = null,
    ): Result<UnbindThirdPartyIdentifiers.Response>

    /**
     * @see [GetOIDCRequestToken]
     */
    suspend fun getOIDCRequestToken(userId: UserId, asUserId: UserId? = null): Result<GetOIDCRequestToken.Response>

    /**
     * @see [Refresh]
     */
    suspend fun refresh(
        refreshToken: String,
    ): Result<Refresh.Response>

    /**
     * @see [GetToken]
     */
    suspend fun getToken(
        asUserId: UserId? = null
    ): Result<UIA<GetToken.Response>>
}

class AuthenticationApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    private val authProvider: MatrixAuthProvider,
    coroutineScope: CoroutineScope
) : AuthenticationApiClient {
    private val oauth2ProviderMetadata by lazy {
        coroutineScope.async {
            // We can use the OAuth2 provider metadata from the authentication provider when it's set to OAuth 2.0. This
            // is not the case when discovering the server or explicitly creating a client for downloading the provider
            // metadata. It's also working as a micro-optimization for reducing the API calls to the homeserver.
            if (authProvider is OAuth2AuthProvider) {
                return@async Result.success(authProvider.providerMetadata)
            }

            log.trace { "Try to request OAuth 2.0 provider metadata from v1 endpoint" }
            baseClient.request(GetOAuth2ProviderMetadataV1).fold(
                onSuccess = { Result.success(it) },
                onFailure = {
                    // We should request the MSC endpoint as a fallback because the update of some homeservers unrolling
                    // the v1 endpoint was delivered a few days ago. To prevent issues with servers providing the
                    // unstable endpoint with the same content, we should also request it if v1 fails. This can be
                    // removed in the future.
                    log.trace { "Failed to request v1 OAuth 2.0 provider metadata, request unstable endpoint" }
                    baseClient.request(GetOAuth2ProviderMetadataUnstable)
                }
            )
        }
    }

    override suspend fun whoAmI(asUserId: UserId?): Result<WhoAmI.Response> =
        baseClient.request(WhoAmI(asUserId))

    override suspend fun isRegistrationTokenValid(
        token: String
    ): Result<Boolean> =
        baseClient.request(IsRegistrationTokenValid(token)).map { it.valid }

    override suspend fun isUsernameAvailable(
        username: String
    ): Result<Boolean> =
        baseClient.request(IsUsernameAvailable(username)).map { it.available }

    override suspend fun getEmailRequestTokenForPassword(
        request: GetEmailRequestTokenForPassword.Request
    ): Result<GetEmailRequestTokenForPassword.Response> =
        baseClient.request(GetEmailRequestTokenForPassword, request)

    override suspend fun getEmailRequestTokenForRegistration(
        request: GetEmailRequestTokenForRegistration.Request
    ): Result<GetEmailRequestTokenForRegistration.Response> =
        baseClient.request(GetEmailRequestTokenForRegistration, request)

    override suspend fun getMsisdnRequestTokenForPassword(
        request: GetMsisdnRequestTokenForPassword.Request
    ): Result<GetMsisdnRequestTokenForPassword.Response> =
        baseClient.request(GetMsisdnRequestTokenForPassword, request)

    override suspend fun getMsisdnRequestTokenForRegistration(
        request: GetMsisdnRequestTokenForRegistration.Request
    ): Result<GetMsisdnRequestTokenForRegistration.Response> =
        baseClient.request(GetMsisdnRequestTokenForRegistration, request)

    override suspend fun register(
        username: String?,
        password: String?,
        accountType: AccountType?,
        deviceId: String?,
        initialDeviceDisplayName: String?,
        inhibitLogin: Boolean?,
        refreshToken: Boolean?,
        isAppservice: Boolean,
    ): Result<UIA<Register.Response>> =
        baseClient.uiaRequest(
            Register(accountType),
            Register.Request(
                username = username,
                password = password,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName,
                inhibitLogin = inhibitLogin,
                refreshToken = refreshToken,
                type = if (isAppservice) LoginType.AppService else null
            )
        )

    override fun getSsoUrl(redirectUrl: String, idpId: String?): String =
        URLBuilder().apply {
            baseClient.baseUrl?.let { takeFrom(it) }
            path(*listOfNotNull("/_matrix/client/v3/login/sso/redirect", idpId).toTypedArray())
            parameters.append("redirectUrl", redirectUrl)
        }.toString()

    override suspend fun getLoginTypes(): Result<Set<LoginType>> =
        baseClient.request(GetLoginTypes).mapCatching { it.flows }

    override suspend fun getOAuth2ProviderMetadata(timeout: Duration): Result<OAuth2ProviderMetadata> =
        try {
            withTimeout(timeout) {
                oauth2ProviderMetadata.await()
            }
        } catch (ex: TimeoutCancellationException) {
            log.trace(ex) { "Request for retrieving OAuth 2.0 metadata timed out!" }
            Result.failure(ex)
        }

    private suspend inline fun <reified O> oauth2Request(
        urlFactory: (OAuth2ProviderMetadata) -> Url,
        block: HttpRequestBuilder.() -> Unit = {}
    ): Result<O> =
        oauth2ProviderMetadata.await().fold(
            onSuccess = { providerMetadata ->
                val response = baseClient.baseClient.post(urlFactory(providerMetadata)) {
                    attributes.put(AuthRequired.attributeKey, AuthRequired.NO)
                    attributes.put(disableMatrixErrorHandling, true)
                    block()
                }

                if (!response.status.isSuccess()) {
                    return@fold Result.failure(response.body<OAuth2ErrorException>())
                }

                Result.success(response.body<O>())
            },
            onFailure = {
                Result.failure(it)
            }
        )

    override suspend fun registerOAuth2Client(metadata: OAuth2ClientMetadata): Result<OAuth2ClientRegistrationResponse> =
        oauth2Request({ providerMetadata -> providerMetadata.registrationEndpoint }) {
            header("Content-Type", ContentType.Application.Json.toString())
            setBody(metadata)
        }

    override suspend fun revokeOAuth2Token(
        token: String,
        tokenTypeHint: TokenTypeHint?,
        clientId: String?
    ): Result<Unit> = oauth2Request({ providerMetadata -> providerMetadata.revocationEndpoint }) {
        contentType(ContentType.Application.FormUrlEncoded)

        setBody(
            Parameters.build {
                append("token", token)
                tokenTypeHint?.let { append("token_type_hint", it.value) }
                clientId?.let { append("client_id", it) }
            }.formUrlEncode()
        )
    }

    override suspend fun getOAuth2Token(
        code: String,
        redirectUrl: String,
        clientId: String,
        codeVerifier: CodeVerifier
    ): Result<OAuth2TokenResponse> = oauth2Request({ providerMetadata -> providerMetadata.tokenEndpoint }) {
        contentType(ContentType.Application.FormUrlEncoded)
        accept(ContentType.Application.Json)

        setBody(
            Parameters.build {
                append("grant_type", GrantType.AuthorizationCode.toString())
                append("code", code)
                append("redirect_uri", redirectUrl)
                append("client_id", clientId)
                append("code_verifier", codeVerifier.toString())
            }.formUrlEncode()
        )
    }

    override fun isOAuth2User(): Boolean = authProvider is OAuth2AuthProvider

    @Deprecated("use login with separated password and token")
    override suspend fun login(
        identifier: IdentifierType?,
        passwordOrToken: String,
        type: LoginType,
        deviceId: String?,
        initialDeviceDisplayName: String?
    ): Result<Login.Response> =
        baseClient.request(
            Login, Login.Request(
                type = type.name,
                identifier = identifier,
                password = if (type is LoginType.Password) passwordOrToken else null,
                token = if (type is LoginType.Token) passwordOrToken else null,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
        )

    override suspend fun login(
        identifier: IdentifierType?,
        password: String?,
        token: String?,
        type: LoginType,
        deviceId: String?,
        initialDeviceDisplayName: String?,
        refreshToken: Boolean?,
    ): Result<Login.Response> =
        baseClient.request(
            Login, Login.Request(
                type = type.name,
                identifier = identifier,
                password = password,
                refreshToken = refreshToken,
                token = token,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
        )

    override suspend fun logout(asUserId: UserId?): Result<Unit> =
        authProvider.logout(this) ?: baseClient.request(Logout(asUserId))

    override suspend fun logoutAll(asUserId: UserId?): Result<Unit> =
        baseClient.request(LogoutAll(asUserId))

    override suspend fun deactivateAccount(
        identityServer: String?,
        erase: Boolean?,
        asUserId: UserId?
    ): Result<UIA<DeactivateAccount.Response>> =
        baseClient.uiaRequest(DeactivateAccount(asUserId), DeactivateAccount.Request(identityServer, erase))

    override suspend fun changePassword(
        newPassword: String,
        logoutDevices: Boolean
    ): Result<UIA<Unit>> =
        baseClient.uiaRequest(ChangePassword, ChangePassword.Request(newPassword, logoutDevices))

    override suspend fun getThirdPartyIdentifiers(
        asUserId: UserId?,
    ): Result<Set<ThirdPartyIdentifier>> =
        baseClient.request(GetThirdPartyIdentifiers(asUserId)).map { it.thirdPartyIdentifiers }

    override suspend fun addThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        asUserId: UserId?,
    ): Result<UIA<Unit>> =
        baseClient.uiaRequest(
            AddThirdPartyIdentifiers(asUserId),
            AddThirdPartyIdentifiers.Request(clientSecret = clientSecret, sessionId = sessionId)
        )

    override suspend fun bindThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        idAccessToken: String,
        idServer: String,
        asUserId: UserId?,
    ): Result<Unit> =
        baseClient.request(
            BindThirdPartyIdentifiers(asUserId),
            BindThirdPartyIdentifiers.Request(
                clientSecret = clientSecret,
                sessionId = sessionId,
                idAccessToken = idAccessToken,
                idServer = idServer
            )
        )

    override suspend fun deleteThirdPartyIdentifiers(
        address: String,
        idServer: String?,
        medium: ThirdPartyIdentifier.Medium,
        asUserId: UserId?,
    ): Result<DeleteThirdPartyIdentifiers.Response> =
        baseClient.request(
            DeleteThirdPartyIdentifiers(asUserId),
            DeleteThirdPartyIdentifiers.Request(
                address = address,
                idServer = idServer,
                medium = medium
            )
        )

    override suspend fun unbindThirdPartyIdentifiers(
        address: String,
        idServer: String?,
        medium: ThirdPartyIdentifier.Medium,
        asUserId: UserId?,
    ): Result<UnbindThirdPartyIdentifiers.Response> =
        baseClient.request(
            UnbindThirdPartyIdentifiers(asUserId),
            UnbindThirdPartyIdentifiers.Request(
                address = address,
                idServer = idServer,
                medium = medium
            )
        )

    override suspend fun getOIDCRequestToken(userId: UserId, asUserId: UserId?): Result<GetOIDCRequestToken.Response> =
        baseClient.request(GetOIDCRequestToken(userId, asUserId))

    override suspend fun refresh(refreshToken: String): Result<Refresh.Response> =
        baseClient.request(Refresh, Refresh.Request(refreshToken))

    override suspend fun getToken(asUserId: UserId?): Result<UIA<GetToken.Response>> =
        baseClient.uiaRequest(GetToken(asUserId))
}
