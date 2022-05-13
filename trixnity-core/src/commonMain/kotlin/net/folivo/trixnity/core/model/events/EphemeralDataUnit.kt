package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EphemeralDataUnit<C : EphemeralEventContent>(
    @SerialName("content") val content: C,
)
