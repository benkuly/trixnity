package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-topic">matrix spec</a>
 */
@Serializable
data class TopicEventContent(
    @SerialName("topic")
    val topic: String
) : StateEventContent