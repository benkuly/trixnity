package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#event-fields">matrix spec</a>
 */
sealed interface Event<C : EventContent> {
    val content: C

    @Serializable
    data class BasicEvent<C : EventContent>(
        @SerialName("content") override val content: C
    ) : Event<C>

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#room-event-fields">matrix spec</a>
     */
    sealed interface RoomEvent<C : RoomEventContent> : Event<C> {
        override val content: C
        val id: EventId
        val sender: UserId
        val roomId: RoomId
        val originTimestamp: Long
        val unsigned: UnsignedRoomEventData?
    }

    @Serializable
    data class MessageEvent<C : MessageEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("event_id") override val id: EventId,
        @SerialName("sender") override val sender: UserId,
        @SerialName("room_id") override val roomId: RoomId,
        @SerialName("origin_server_ts") override val originTimestamp: Long,
        @SerialName("unsigned") override val unsigned: UnsignedMessageEventData? = null
    ) : RoomEvent<C>

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#state-event-fields">matrix spec</a>
     */
    @Serializable
    data class StateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("event_id") override val id: EventId,
        @SerialName("sender") override val sender: UserId,
        @SerialName("room_id") override val roomId: RoomId,
        @SerialName("origin_server_ts") override val originTimestamp: Long,
        @SerialName("unsigned") override val unsigned: UnsignedStateEventData<C>? = null,
        @SerialName("state_key") val stateKey: String,
    ) : RoomEvent<C>

    @Serializable
    data class StrippedStateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId,
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("state_key") val stateKey: String
    ) : Event<C>

    @Serializable
    data class ToDeviceEvent<C : ToDeviceEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId
    ) : Event<C>

    @Serializable
    data class EphemeralEvent<C : EphemeralEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId? = null,
        @SerialName("sender") val sender: UserId? = null
    ) : Event<C>

    @Serializable
    data class OlmEvent<C : EventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId,
        @SerialName("recipient") val recipient: UserId,
        @SerialName("recipient_keys") val recipientKeys: Keys,
        @SerialName("keys") val senderKeys: Keys
    ) : Event<C>

    @Serializable
    data class MegolmEvent<C : MessageEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId
    ) : Event<C>

    @Serializable
    data class AccountDataEvent<C : AccountDataEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId? = null,
    ): Event<C>
}