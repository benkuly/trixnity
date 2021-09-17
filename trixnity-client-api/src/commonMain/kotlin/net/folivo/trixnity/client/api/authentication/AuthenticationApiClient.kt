package net.folivo.trixnity.client.api.authentication

import io.ktor.client.*
import io.ktor.client.request.*

class AuthenticationApiClient(
    private val httpClient: HttpClient,
) {

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-register">matrix spec</a>
     */
    suspend fun register(
        authenticationType: String? = null, // TODO in client spec mandatory
        authenticationSession: String? = null,
        username: String? = null,
        password: String? = null,
        accountType: AccountType? = null,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null,
        inhibitLogin: Boolean? = null,
        isAppservice: Boolean = false // TODO why is the spec so inconsistent?
    ): RegisterResponse {
        return httpClient.post {
            url("/_matrix/client/r0/register")
            parameter("kind", accountType?.value)
            body = RegisterRequest(
                RegisterRequest.Auth(authenticationType, authenticationSession),
                username,
                password,
                deviceId,
                initialDeviceDisplayName,
                inhibitLogin,
                if (isAppservice) "m.login.application_service" else null
            )
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-login">matrix spec</a>
     */
    suspend fun getLoginTypes(): Set<LoginType> {
        return httpClient.get<GetLoginTypesResponse> {
            url("/_matrix/client/r0/login")
        }.flows
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-login">matrix spec</a>
     */
    suspend fun login(
        identifier: IdentifierType,
        passwordOrToken: String,
        type: LoginType = LoginType.Password,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null
    ): LoginResponse {
        return httpClient.post {
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
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-logout">matrix spec</a>
     */
    suspend fun logout() {
        return httpClient.get {
            url("/_matrix/client/r0/logout")
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-logout-all">matrix spec</a>
     */
    suspend fun logoutAll() {
        return httpClient.get {
            url("/_matrix/client/r0/logout/all")
        }
    }
}