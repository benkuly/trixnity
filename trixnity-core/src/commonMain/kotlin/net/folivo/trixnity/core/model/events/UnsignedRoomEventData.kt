package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface UnsignedRoomEventData {
    val age: Long?
    val redactedBecause: ClientEvent<*>?
    val transactionId: String?

    @Serializable
    data class UnsignedMessageEventData(
        @SerialName("age") override val age: Long? = null,
        // TODO redacted_because does not have a room_id -> custom deserializer needed
        @SerialName("redacted_because") @Contextual override val redactedBecause: ClientEvent<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null
    ) : UnsignedRoomEventData

    @Serializable
    data class UnsignedStateEventData<C : StateEventContent>(
        @SerialName("age") override val age: Long? = null,
        // TODO redacted_because does not have a room_id -> custom deserializer needed
        @SerialName("redacted_because") @Contextual override val redactedBecause: ClientEvent<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("prev_content") val previousContent: C? = null
    ) : UnsignedRoomEventData
}