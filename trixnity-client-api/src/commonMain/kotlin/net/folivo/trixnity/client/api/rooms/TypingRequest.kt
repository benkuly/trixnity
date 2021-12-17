package net.folivo.trixnity.client.api.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TypingRequest(
    @SerialName("typing") val typing: Boolean,
    @SerialName("timeout") val timeout: Int? = null,
)