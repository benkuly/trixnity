package de.connect2x.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import de.connect2x.trixnity.clientserverapi.model.authentication.*
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import de.connect2x.trixnity.core.model.UserId

private val log = KotlinLogging.logger { }

interface AuthenticationApiClient {
    /**
     * @see [WhoAmI]
     */
    suspend fun whoAmI(): Result<WhoAmI.Response>

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
     * @see [de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata]
     */
    suspend fun getOAuth2ServerMetadata(): Result<ServerMetadata>

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
    suspend fun logout(): Result<Unit>

    /**
     * @see [LogoutAll]
     */
    suspend fun logoutAll(): Result<Unit>

    /**
     * @see [DeactivateAccount]
     */
    suspend fun deactivateAccount(
        identityServer: String? = null,
        erase: Boolean? = null,
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
    suspend fun getThirdPartyIdentifiers(): Result<Set<ThirdPartyIdentifier>>

    /**
     * @see [AddThirdPartyIdentifiers]
     */
    suspend fun addThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
    ): Result<UIA<Unit>>

    /**
     * @see [BindThirdPartyIdentifiers]
     */
    suspend fun bindThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        idAccessToken: String,
        idServer: String,
    ): Result<Unit>

    /**
     * @see [DeleteThirdPartyIdentifiers]
     */
    suspend fun deleteThirdPartyIdentifiers(
        address: String,
        idServer: String? = null,
        medium: ThirdPartyIdentifier.Medium,
    ): Result<DeleteThirdPartyIdentifiers.Response>

    /**
     * @see [UnbindThirdPartyIdentifiers]
     */
    suspend fun unbindThirdPartyIdentifiers(
        address: String,
        idServer: String? = null,
        medium: ThirdPartyIdentifier.Medium,
    ): Result<UnbindThirdPartyIdentifiers.Response>

    /**
     * @see [GetOIDCRequestToken]
     */
    suspend fun getOIDCRequestToken(userId: UserId): Result<GetOIDCRequestToken.Response>

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
    ): Result<UIA<GetToken.Response>>
}

class AuthenticationApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    private val authProvider: MatrixClientAuthProvider,
) : AuthenticationApiClient {
    override suspend fun whoAmI(): Result<WhoAmI.Response> =
        baseClient.request(WhoAmI)

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
            takeFrom(authProvider.baseUrl)
            path(*listOfNotNull("/_matrix/client/v3/login/sso/redirect", idpId).toTypedArray())
            parameters.append("redirectUrl", redirectUrl)
        }.toString()

    override suspend fun getLoginTypes(): Result<Set<LoginType>> =
        baseClient.request(GetLoginTypes).mapCatching { it.flows }

    override suspend fun getOAuth2ServerMetadata(): Result<ServerMetadata> =
        baseClient.request(GetOAuth2ServerMetadata)

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

    override suspend fun logout(): Result<Unit> = runCatching {
        authProvider.logout()?.getOrThrow()
        baseClient.request(Logout).getOrThrow()
    }

    override suspend fun logoutAll(): Result<Unit> =
        baseClient.request(LogoutAll)

    override suspend fun deactivateAccount(
        identityServer: String?,
        erase: Boolean?,
    ): Result<UIA<DeactivateAccount.Response>> =
        baseClient.uiaRequest(DeactivateAccount, DeactivateAccount.Request(identityServer, erase))

    override suspend fun changePassword(
        newPassword: String,
        logoutDevices: Boolean
    ): Result<UIA<Unit>> =
        baseClient.uiaRequest(ChangePassword, ChangePassword.Request(newPassword, logoutDevices))

    override suspend fun getThirdPartyIdentifiers(
    ): Result<Set<ThirdPartyIdentifier>> =
        baseClient.request(GetThirdPartyIdentifiers).map { it.thirdPartyIdentifiers }

    override suspend fun addThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
    ): Result<UIA<Unit>> =
        baseClient.uiaRequest(
            AddThirdPartyIdentifiers,
            AddThirdPartyIdentifiers.Request(clientSecret = clientSecret, sessionId = sessionId)
        )

    override suspend fun bindThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        idAccessToken: String,
        idServer: String,
    ): Result<Unit> =
        baseClient.request(
            BindThirdPartyIdentifiers,
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
    ): Result<DeleteThirdPartyIdentifiers.Response> =
        baseClient.request(
            DeleteThirdPartyIdentifiers,
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
    ): Result<UnbindThirdPartyIdentifiers.Response> =
        baseClient.request(
            UnbindThirdPartyIdentifiers,
            UnbindThirdPartyIdentifiers.Request(
                address = address,
                idServer = idServer,
                medium = medium
            )
        )

    override suspend fun getOIDCRequestToken(userId: UserId): Result<GetOIDCRequestToken.Response> =
        baseClient.request(GetOIDCRequestToken(userId))

    override suspend fun refresh(refreshToken: String): Result<Refresh.Response> =
        baseClient.request(Refresh, Refresh.Request(refreshToken))

    override suspend fun getToken(): Result<UIA<GetToken.Response>> =
        baseClient.uiaRequest(GetToken)
}
