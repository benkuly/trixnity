package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#types-of-room-events">matrix spec</a>
 */
sealed interface ClientEvent<C : EventContent> : Event<C> {
    /**
     * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#room-event-fields">matrix spec</a>
     */
    sealed interface RoomEvent<C : RoomEventContent> : ClientEvent<C> {
        val id: EventId
        val sender: UserId
        val roomId: RoomId
        val originTimestamp: Long
        val unsigned: UnsignedRoomEventData?

        @Serializable
        data class MessageEvent<C : MessageEventContent>(
            @SerialName("content") override val content: C,
            @SerialName("event_id") override val id: EventId,
            @SerialName("sender") override val sender: UserId,
            @SerialName("room_id") override val roomId: RoomId,
            @SerialName("origin_server_ts") override val originTimestamp: Long,
            @SerialName("unsigned") override val unsigned: UnsignedRoomEventData.UnsignedMessageEventData? = null
        ) : RoomEvent<C>

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#types-of-room-events">matrix spec</a>
         */
        @Serializable
        data class StateEvent<C : StateEventContent>(
            @SerialName("content") override val content: C,
            @SerialName("event_id") override val id: EventId,
            @SerialName("sender") override val sender: UserId,
            @SerialName("room_id") override val roomId: RoomId,
            @SerialName("origin_server_ts") override val originTimestamp: Long,
            @SerialName("unsigned") override val unsigned: UnsignedRoomEventData.UnsignedStateEventData<C>? = null,
            @SerialName("state_key") override val stateKey: String,
        ) : RoomEvent<C>, StateBaseEvent<C>
    }

    /**
     * This is just an internal base class for [RoomEvent.StateEvent] and [StrippedStateEvent].
     */
    sealed interface StateBaseEvent<C : StateEventContent> : ClientEvent<C> {
        override val content: C
        val id: EventId?
        val sender: UserId
        val roomId: RoomId?
        val originTimestamp: Long?
        val unsigned: UnsignedRoomEventData.UnsignedStateEventData<C>?
        val stateKey: String
    }

    @Serializable
    data class StrippedStateEvent<C : StateEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("event_id") override val id: EventId? = null,
        @SerialName("sender") override val sender: UserId,
        @SerialName("room_id") override val roomId: RoomId? = null,
        @SerialName("origin_server_ts") override val originTimestamp: Long? = null,
        @SerialName("unsigned") override val unsigned: UnsignedRoomEventData.UnsignedStateEventData<C>? = null,
        @SerialName("state_key") override val stateKey: String,
    ) : ClientEvent<C>, StateBaseEvent<C>

    @Serializable
    data class ToDeviceEvent<C : ToDeviceEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId
    ) : ClientEvent<C>

    // TODO could be split into GlobalEphemeralEvent and RoomEphemeralEvent
    @Serializable
    data class EphemeralEvent<C : EphemeralEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("sender") val sender: UserId? = null,
        @SerialName("room_id") val roomId: RoomId? = null
    ) : ClientEvent<C>

    @Serializable
    data class RoomAccountDataEvent<C : RoomAccountDataEventContent>(
        @SerialName("content") override val content: C,
        @SerialName("room_id") val roomId: RoomId,
        // This does not actually exist. We use it to circumvent inconsistent spec.
        @SerialName("key") val key: String = ""
    ) : ClientEvent<C>

    @Serializable
    data class GlobalAccountDataEvent<C : GlobalAccountDataEventContent>(
        @SerialName("content") override val content: C,
        // This does not actually exist. We use it to circumvent inconsistent spec.
        @SerialName("key") val key: String = ""
    ) : ClientEvent<C>
}