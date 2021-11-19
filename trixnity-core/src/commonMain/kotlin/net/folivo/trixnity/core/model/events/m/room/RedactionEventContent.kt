package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.MessageEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-create">matrix spec</a>
 */
@Serializable
data class RedactionEventContent(
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("redacts")
    val redacts: EventId
) : MessageEventContent