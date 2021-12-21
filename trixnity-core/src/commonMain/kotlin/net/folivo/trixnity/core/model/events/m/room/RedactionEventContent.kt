package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.MessageEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mroomredaction">matrix spec</a>
 */
@Serializable
data class RedactionEventContent(
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("redacts")
    val redacts: EventId
) : MessageEventContent