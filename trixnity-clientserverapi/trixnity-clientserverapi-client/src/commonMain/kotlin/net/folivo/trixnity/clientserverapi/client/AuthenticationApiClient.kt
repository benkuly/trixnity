package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.core.model.UserId

interface IAuthenticationApiClient {
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
    ): Result<Unit>

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
        isAppservice: Boolean = false // TODO why is the spec so inconsistent?
    ): Result<UIA<Register.Response>>

    /**
     * @see [GetLoginTypes]
     */
    suspend fun getLoginTypes(): Result<Set<LoginType>>

    /**
     * @see [Login]
     */
    suspend fun login(
        identifier: IdentifierType,
        passwordOrToken: String,
        type: LoginType = LoginType.Password,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null
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
}

class AuthenticationApiClient(
    private val httpClient: MatrixClientServerApiHttpClient
) : IAuthenticationApiClient {

    override suspend fun whoAmI(asUserId: UserId?): Result<WhoAmI.Response> =
        httpClient.request(WhoAmI(asUserId))

    override suspend fun isRegistrationTokenValid(
        token: String
    ): Result<Boolean> =
        httpClient.request(IsRegistrationTokenValid(token)).map { it.valid }

    override suspend fun isUsernameAvailable(
        username: String
    ): Result<Unit> =
        httpClient.request(IsUsernameAvailable(username))

    override suspend fun getEmailRequestTokenForPassword(
        request: GetEmailRequestTokenForPassword.Request
    ): Result<GetEmailRequestTokenForPassword.Response> =
        httpClient.request(GetEmailRequestTokenForPassword, request)

    override suspend fun getEmailRequestTokenForRegistration(
        request: GetEmailRequestTokenForRegistration.Request
    ): Result<GetEmailRequestTokenForRegistration.Response> =
        httpClient.request(GetEmailRequestTokenForRegistration, request)

    override suspend fun getMsisdnRequestTokenForPassword(
        request: GetMsisdnRequestTokenForPassword.Request
    ): Result<GetMsisdnRequestTokenForPassword.Response> =
        httpClient.request(GetMsisdnRequestTokenForPassword, request)

    override suspend fun getMsisdnRequestTokenForRegistration(
        request: GetMsisdnRequestTokenForRegistration.Request
    ): Result<GetMsisdnRequestTokenForRegistration.Response> =
        httpClient.request(GetMsisdnRequestTokenForRegistration, request)

    override suspend fun register(
        username: String?,
        password: String?,
        accountType: AccountType?,
        deviceId: String?,
        initialDeviceDisplayName: String?,
        inhibitLogin: Boolean?,
        isAppservice: Boolean  // TODO why is the spec so inconsistent?
    ): Result<UIA<Register.Response>> =
        httpClient.uiaRequest(
            Register(accountType),
            Register.Request(
                username = username,
                password = password,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName,
                inhibitLogin = inhibitLogin,
                type = if (isAppservice) "m.login.application_service" else null
            )
        )

    override suspend fun getLoginTypes(): Result<Set<LoginType>> =
        httpClient.request(GetLoginTypes).mapCatching { it.flows }

    override suspend fun login(
        identifier: IdentifierType,
        passwordOrToken: String,
        type: LoginType,
        deviceId: String?,
        initialDeviceDisplayName: String?
    ): Result<Login.Response> =
        httpClient.request(
            Login, Login.Request(
                type = type.name,
                identifier = identifier,
                password = if (type is LoginType.Password) passwordOrToken else null,
                token = if (type is LoginType.Token) passwordOrToken else null,
                deviceId = deviceId,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
        )

    override suspend fun logout(asUserId: UserId?): Result<Unit> =
        httpClient.request(Logout(asUserId))

    override suspend fun logoutAll(asUserId: UserId?): Result<Unit> =
        httpClient.request(LogoutAll(asUserId))

    override suspend fun deactivateAccount(
        identityServer: String?,
        asUserId: UserId?
    ): Result<UIA<DeactivateAccount.Response>> =
        httpClient.uiaRequest(DeactivateAccount(asUserId), DeactivateAccount.Request(identityServer))

    override suspend fun changePassword(
        newPassword: String,
        logoutDevices: Boolean
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest(ChangePassword, ChangePassword.Request(newPassword, logoutDevices))

    override suspend fun getThirdPartyIdentifiers(
        asUserId: UserId?,
    ): Result<Set<ThirdPartyIdentifier>> =
        httpClient.request(GetThirdPartyIdentifiers(asUserId)).map { it.thirdPartyIdentifiers }

    override suspend fun addThirdPartyIdentifiers(
        clientSecret: String,
        sessionId: String,
        asUserId: UserId?,
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest(
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
        httpClient.request(
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
        httpClient.request(
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
        httpClient.request(
            UnbindThirdPartyIdentifiers(asUserId),
            UnbindThirdPartyIdentifiers.Request(
                address = address,
                idServer = idServer,
                medium = medium
            )
        )

    override suspend fun getOIDCRequestToken(userId: UserId, asUserId: UserId?): Result<GetOIDCRequestToken.Response> =
        httpClient.request(GetOIDCRequestToken(userId.e(), asUserId))
}