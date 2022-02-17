package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeactivateAccountRequest(
    @SerialName("id_server")
    val identityServer: String?,
)