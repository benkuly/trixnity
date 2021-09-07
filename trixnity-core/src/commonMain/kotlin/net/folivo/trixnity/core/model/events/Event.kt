package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.crypto.Keys

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
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
        @SerialName("unsigned") val unsigned: UnsignedData? = null
    ) : Event<C>()

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#state-event-fields">matrix spec</a>
     */
    @Serializable
    data class StateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("event_id") val id: EventId,
        @SerialName("sender") val sender: UserId,
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
        @SerialName("unsigned") val unsigned: UnsignedData? = null,
        @SerialName("state_key") val stateKey: String,
        @SerialName("prev_content") val previousContent: C? = null
    ) : Event<C>()

    @Serializable
    data class StrippedStateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId,
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("state_key") val stateKey: String
    ) : Event<C>()

    @Serializable
    data class ToDeviceEvent<C : ToDeviceEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId
    ) : Event<C>()

    @Serializable
    data class EphemeralEvent<C : EphemeralEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId? = null,
        @SerialName("sender") val sender: UserId? = null
    ) : Event<C>()

    @Serializable
    data class OlmEvent<C : EventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId,
        @SerialName("recipient") val recipient: UserId,
        @SerialName("recipient_keys") val recipientKeys: Keys,
        @SerialName("keys") val senderKeys: Keys
    ) : Event<C>()

    @Serializable
    data class MegolmEvent<C : RoomEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId
    ) : Event<C>()
}