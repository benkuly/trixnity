package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEvent.CreateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-create">matrix spec</a>
 */
@Serializable
data class CreateEvent(
    @SerialName("content") override val content: CreateEventContent,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("prev_content") override val previousContent: CreateEventContent? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "m.room.create"
) : StateEvent<CreateEventContent> {

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
    ) {
        @Serializable
        data class PreviousRoom(
            @SerialName("room_id")
            val roomId: RoomId,
            @SerialName("event_id")
            val eventId: EventId
        )
    }
}