package net.folivo.matrix.restclient.api.user

import io.ktor.client.*
import io.ktor.client.request.*
import net.folivo.trixnity.client.rest.api.user.AccountType
import net.folivo.trixnity.core.model.MatrixId.UserId

class UserApiClient(private val httpClient: HttpClient) {

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-register">matrix spec</a>
     */
    suspend fun register(
        authenticationType: String,
        authenticationSession: String? = null,
        username: String? = null,
        password: String? = null,
        accountType: AccountType? = null,
        deviceId: String? = null,
        initialDeviceDisplayName: String? = null,
        inhibitLogin: Boolean? = null
    ): RegisterResponse {
        return httpClient.post {
            url("/r0/register")
            parameter("kind", accountType?.value)
            body = mapOf(
                "auth" to mapOf(
                    "type" to authenticationType,
                    "session" to authenticationSession
                ),
                "username" to username,
                "password" to password,
                "device_id" to deviceId,
                "initial_device_display_name" to initialDeviceDisplayName,
                "inhibit_login" to inhibitLogin
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
            url("/r0/profile/${userId.full}/displayname")
            parameter("user_id", asUserId?.full)
            body = mapOf("displayname" to displayName)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-account-whoami">matrix spec</a>
     */
    suspend fun whoAmI(asUserId: UserId? = null): UserId {
        return httpClient.get {
            url("/r0/account/whoami")
            parameter("user_id", asUserId?.full)
        }
    }
}