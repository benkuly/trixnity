package net.folivo.trixnity.client.api.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeactivateAccountRequest(
    @SerialName("id_server")
    val identityServer: String?,
)