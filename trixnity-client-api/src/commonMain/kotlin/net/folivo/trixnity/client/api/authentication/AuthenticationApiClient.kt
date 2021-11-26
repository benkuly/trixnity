package net.folivo.trixnity.client.api.authentication

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import net.folivo.trixnity.client.api.MatrixHttpClient
import net.folivo.trixnity.client.api.uia.UIA

class AuthenticationApiClient(
    private val httpClient: MatrixHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3registeravailable">matrix spec</a>
     */
    suspend fun isUsernameAvailable(
        username: String
    ) {
        httpClient.request<Unit> {
            method = Get
            url("/_matrix/client/r0/register/available")
            parameter("username", username)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3register">matrix spec</a>
     */
    suspend fun register(
        username: String? = null,
        password: String? = null,
        accountType: AccountType? = null,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null,
        inhibitLogin: Boolean? = null,
        isAppservice: Boolean = false // TODO why is the spec so inconsistent?
    ): UIA<RegisterResponse> {
        return httpClient.uiaRequest(
            body = RegisterRequest(
                username,
                password,
                deviceId,
                initialDeviceDisplayName,
                inhibitLogin,
                if (isAppservice) "m.login.application_service" else null
            )
        ) {
            method = Post
            url("/_matrix/client/r0/register")
            parameter("kind", accountType?.value)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3login">matrix spec</a>
     */
    suspend fun getLoginTypes(): Set<LoginType> {
        return httpClient.request<GetLoginTypesResponse> {
            method = Get
            url("/_matrix/client/r0/login")
        }.flows
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3login">matrix spec</a>
     */
    suspend fun login(
        identifier: IdentifierType,
        passwordOrToken: String,
        type: LoginType = LoginType.Password,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null
    ): LoginResponse {
        return httpClient.request {
            method = Post
            url("/_matrix/client/r0/login")
            body = LoginRequest(
                type.name,
                identifier,
                if (type == LoginType.Password) passwordOrToken else null,
                if (type == LoginType.Token) passwordOrToken else null,
                deviceId,
                initialDeviceDisplayName
            )
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3logout">matrix spec</a>
     */
    suspend fun logout() {
        return httpClient.request {
            method = Post
            url("/_matrix/client/r0/logout")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3logoutall">matrix spec</a>
     */
    suspend fun logoutAll() {
        return httpClient.request {
            method = Post
            url("/_matrix/client/r0/logout/all")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3accountdeactivate">matrix spec</a>
     */
    suspend fun deactivateAccount(
        identityServer: String? = null
    ): UIA<Unit> {
        return httpClient.uiaRequest(
            body = DeactivateAccountRequest(identityServer)
        ) {
            method = Post
            url("/_matrix/client/r0/account/deactivate")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3accountpassword">matrix spec</a>
     */
    suspend fun changePassword(
        newPassword: String,
        logoutDevices: Boolean = false
    ): UIA<Unit> {
        return httpClient.uiaRequest(
            body = ChangePasswordRequest(newPassword, logoutDevices),
        ) {
            method = Post
            url("/_matrix/client/r0/account/password")
        }
    }
}