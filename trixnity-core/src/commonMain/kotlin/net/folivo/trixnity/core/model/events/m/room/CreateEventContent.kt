package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mroomcreate">matrix spec</a>
 */
@Serializable
data class CreateEventContent(
    @SerialName("creator")
    val creator: UserId,
    @SerialName("m.federate")
    val federate: Boolean = true,
    @SerialName("room_version")
    val roomVersion: String = "1",
    @SerialName("predecessor")
    val predecessor: PreviousRoom? = null
) : StateEventContent {
    @Serializable
    data class PreviousRoom(
        @SerialName("room_id")
        val roomId: RoomId,
        @SerialName("event_id")
        val eventId: EventId
    )
}