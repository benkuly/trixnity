package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#event-fields">matrix spec</a>
 */
@Serializable
sealed class Event<C : EventContent> {
    abstract val content: C

    @Serializable
    data class BasicEvent<C : EventContent>(
        @SerialName("content") override val content: C
    ) : Event<C>()

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#room-event-fields">matrix spec</a>
     */
    @Serializable
    data class RoomEvent<C : RoomEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("event_id") val id: EventId,
        @SerialName("sender") val sender: UserId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
        @SerialName("room_id") val roomId: RoomId?,
        @SerialName("unsigned") val unsigned: UnsignedData? = null,
    ) : Event<C>()

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#state-event-fields">matrix spec</a>
     */
    @Serializable
    data class StateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("event_id") val id: EventId,
        @SerialName("sender") val sender: UserId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
        @SerialName("room_id") val roomId: RoomId?,
        @SerialName("unsigned") val unsigned: UnsignedData? = null,
        @SerialName("state_key") val stateKey: String,
        @SerialName("prev_content") val previousContent: C? = null
    ) : Event<C>()

    @Serializable
    data class StrippedStateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("state_key") val stateKey: String,
        @SerialName("sender") val sender: UserId
    ) : Event<C>()
}