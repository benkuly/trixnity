package net.folivo.trixnity.client.api.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class LoginResponse(
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