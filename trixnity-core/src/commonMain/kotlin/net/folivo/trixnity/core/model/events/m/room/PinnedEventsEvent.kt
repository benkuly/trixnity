package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.StateEvent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-pinned-events">matrix spec</a>
 */
@Serializable
data class PinnedEventsEvent(
    @SerialName("content") override val content: PinnedEventsEventContent,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("prev_content") override val previousContent: PinnedEventsEventContent? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "m.room.pinned_events"
) : StateEvent<PinnedEventsEvent.PinnedEventsEventContent> {

    @Serializable
    data class PinnedEventsEventContent(
        @SerialName("pinned")
        val pinned: List<String> = listOf()
    )
}