package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.keys.Keys

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#types-of-room-events">matrix spec</a>
 */
sealed interface Event<C : EventContent> {
    val content: C

    @Serializable
    data class UnknownEvent(
        override val content: EmptyEventContent,
        val type: String,
        val raw: JsonObject
    ) : Event<EmptyEventContent>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#room-event-fields">matrix spec</a>
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
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#types-of-room-events">matrix spec</a>
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
    data class InitialStateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("state_key") val stateKey: String
    ) : Event<C>

    @Serializable
    data class ToDeviceEvent<C : ToDeviceEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId
    ) : Event<C>

    // TODO could be split into GlobalEphemeralEvent and RoomEphemeralEvent
    @Serializable
    data class EphemeralEvent<C : EphemeralEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId? = null,
        @SerialName("room_id") val roomId: RoomId? = null
    ) : Event<C>

    @Serializable
    data class OlmEvent<C : EventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId,
        @SerialName("keys") val senderKeys: Keys,
        @SerialName("recipient") val recipient: UserId,
        @SerialName("recipient_keys") val recipientKeys: Keys
    ) : Event<C>

    @Serializable
    data class MegolmEvent<C : RoomEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId
    ) : Event<C>

    @Serializable
    data class RoomAccountDataEvent<C : RoomAccountDataEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId,
        // This does not actually exist. We use it to circumvent inconsistent spec.
        @SerialName("key") val key: String = ""
    ) : Event<C>

    @Serializable
    data class GlobalAccountDataEvent<C : GlobalAccountDataEventContent>(
        @SerialName("content") override val content: C,
        // This does not actually exist. We use it to circumvent inconsistent spec.
        @SerialName("key") val key: String = ""
    ) : Event<C>
}