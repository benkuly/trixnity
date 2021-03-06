package net.folivo.trixnity.client.rest.api.user

import io.ktor.client.*
import io.ktor.client.request.*
import net.folivo.trixnity.client.rest.e
import net.folivo.trixnity.core.model.MatrixId.UserId

class UserApiClient(private val httpClient: HttpClient) {

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
            url("/r0/register")
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
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-profile-userid-displayname">matrix spec</a>
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ) {
        return httpClient.put {
            url("/r0/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
            body = mapOf("displayname" to displayName)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-account-whoami">matrix spec</a>
     */
    suspend fun whoAmI(asUserId: UserId? = null): UserId {
        return httpClient.get<WhoAmIResponse> {
            url("/r0/account/whoami")
            parameter("user_id", asUserId)
        }.userId
    }
}