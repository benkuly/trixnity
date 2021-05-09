package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.RoomEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-create">matrix spec</a>
 */
@Serializable
data class RedactionEventContent(
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("redacts")
    val redacts: MatrixId.EventId
) : RoomEventContent