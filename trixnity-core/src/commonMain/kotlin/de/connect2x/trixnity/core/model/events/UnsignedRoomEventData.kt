package de.connect2x.trixnity.core.model.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import de.connect2x.trixnity.core.model.events.m.Relations
import de.connect2x.trixnity.core.model.events.m.room.Membership

sealed interface UnsignedRoomEventData {
    val age: Long?
    val redactedBecause: MessageEvent<*>?
    val transactionId: String?
    val relations: Relations?
    val membership: Membership?

    @Serializable
    data class UnsignedMessageEventData(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redacted_because") override val redactedBecause: @Contextual MessageEvent<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("m.relations") override val relations: Relations? = null,
        @SerialName("membership") override val membership: Membership? = null
    ) : UnsignedRoomEventData

    @Serializable
    data class UnsignedStateEventData(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redacted_because") override val redactedBecause: @Contextual MessageEvent<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("prev_content") val previousContent: @Contextual StateEventContent? = null,
        @SerialName("m.relations") override val relations: Relations? = null,
        @SerialName("membership") override val membership: Membership? = null,
        @SerialName("invite_room_state") val inviteRoomState: List<@Contextual StrippedStateEvent<*>>? = null,
        @SerialName("knock_room_state") val knockRoomState: List<@Contextual StrippedStateEvent<*>>? = null,
    ) : UnsignedRoomEventData
}