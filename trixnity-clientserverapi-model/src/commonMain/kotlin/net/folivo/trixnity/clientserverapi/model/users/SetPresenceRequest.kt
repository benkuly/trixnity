package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetPresenceRequest(
    @SerialName("presence") val presence: String,
    @SerialName("status_msg") val statusMessage: String?
)