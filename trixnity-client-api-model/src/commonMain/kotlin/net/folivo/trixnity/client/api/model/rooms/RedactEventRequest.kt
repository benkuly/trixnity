package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RedactEventRequest (
    @SerialName("reason") val reason: String?
)