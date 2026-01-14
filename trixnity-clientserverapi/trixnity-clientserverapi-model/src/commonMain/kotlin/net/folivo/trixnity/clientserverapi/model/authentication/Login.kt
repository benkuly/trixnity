package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3login">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/login")
@HttpMethod(POST)
@Auth(AuthRequired.OPTIONAL)
object Login : MatrixEndpoint<Login.Request, Login.Response> {
    @Serializable
    data class Request(
        @SerialName("type")
        val type: String,
        @SerialName("identifier")
        val identifier: IdentifierType? = null,
        @SerialName("password")
        val password: String? = null,
        @SerialName("refresh_token")
        val refreshToken: Boolean? = null,
        @SerialName("token")
        val token: String? = null,
        @SerialName("device_id")
        val deviceId: String? = null,
        @SerialName("initial_device_display_name")
        val initialDeviceDisplayName: String? = null
    )

    @Serializable
    data class Response(
        @SerialName("user_id")
        val userId: UserId,
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("expires_in_ms")
        val accessTokenExpiresInMs: Long? = null,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("well_known")
        val discoveryInformation: DiscoveryInformation? = null
    )
}