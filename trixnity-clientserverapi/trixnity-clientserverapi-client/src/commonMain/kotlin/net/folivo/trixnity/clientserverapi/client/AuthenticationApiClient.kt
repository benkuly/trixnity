package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.core.model.UserId

class AuthenticationApiClient(
    private val httpClient: MatrixClientServerApiHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3registeravailable">matrix spec</a>
     */
    suspend fun isUsernameAvailable(
        username: String
    ): Result<Unit> =
        httpClient.request(IsUsernameAvailable(username))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3register">matrix spec</a>
     */
    suspend fun register(
        username: String? = null,
        password: String? = null,
        accountType: AccountType? = null,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null,
        inhibitLogin: Boolean? = null,
        isAppservice: Boolean = false // TODO why is the spec so inconsistent?
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

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3login">matrix spec</a>
     */
    suspend fun getLoginTypes(): Result<Set<LoginType>> =
        httpClient.request(GetLoginTypes).mapCatching { it.flows }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3login">matrix spec</a>
     */
    suspend fun login(
        identifier: IdentifierType,
        passwordOrToken: String,
        type: LoginType = LoginType.Password,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null
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

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3logout">matrix spec</a>
     */
    suspend fun logout(asUserId: UserId? = null): Result<Unit> =
        httpClient.request(Logout(asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3logoutall">matrix spec</a>
     */
    suspend fun logoutAll(asUserId: UserId? = null): Result<Unit> =
        httpClient.request(LogoutAll(asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountdeactivate">matrix spec</a>
     */
    suspend fun deactivateAccount(
        identityServer: String? = null,
        asUserId: UserId? = null
    ): Result<UIA<DeactivateAccount.Response>> =
        httpClient.uiaRequest(DeactivateAccount(asUserId), DeactivateAccount.Request(identityServer))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountpassword">matrix spec</a>
     */
    suspend fun changePassword(
        newPassword: String,
        logoutDevices: Boolean = false
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest(ChangePassword, ChangePassword.Request(newPassword, logoutDevices))
}