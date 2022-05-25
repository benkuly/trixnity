package net.folivo.trixnity.clientserverapi.model.uia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThirdPidCredentials(
    @SerialName("sid") val sid: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("id_server") val identityServer: String?,
    @SerialName("id_access_token") val identityServerAccessToken: String?
)
