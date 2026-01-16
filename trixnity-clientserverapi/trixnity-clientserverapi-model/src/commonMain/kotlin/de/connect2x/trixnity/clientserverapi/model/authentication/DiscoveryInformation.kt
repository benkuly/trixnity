package de.connect2x.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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