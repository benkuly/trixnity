package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/login")
object Login : MatrixJsonEndpoint<Login.Request, Login.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("type")
        val type: LoginType,
        @SerialName("identifier")
        val identifier: IdentifierType,
        @SerialName("password")
        val password: String? = null,
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
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("well_known")
        val discoveryInformation: DiscoveryInformation? = null
    ) {
        @Serializable
        data class DiscoveryInformation(
            @SerialName("m.homeserver")
            val homeserver: HomeserverInformation,
            @SerialName("m.identity_server")
            val identityServer: IdentityServerInformation? = null,
        ) {
            @Serializable
            data class HomeserverInformation(
                @SerialName("base_url")
                val baseUrl: String
            )

            @Serializable
            data class IdentityServerInformation(
                @SerialName("base_url")
                val baseUrl: String
            )
        }
    }
}