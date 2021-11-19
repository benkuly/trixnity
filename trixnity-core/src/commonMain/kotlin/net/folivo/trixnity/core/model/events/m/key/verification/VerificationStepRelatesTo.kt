package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId

@Serializable
data class VerificationStepRelatesTo(
    @SerialName("event_id")
    val eventId: EventId,
    @SerialName("rel_type")
    val type: String = "m.reference"
)