package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface UnsignedRoomEventData {
    val age: Long?
    val redactedBecause: Event<*>?
    val transactionId: String?
    val aggregations: Aggregations?

    @Serializable
    data class UnsignedMessageEventData(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redacted_because") override val redactedBecause: @Contextual Event<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("m.relations") override val aggregations: Aggregations? = null,
    ) : UnsignedRoomEventData

    @Serializable
    data class UnsignedStateEventData<C : StateEventContent>(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redacted_because") override val redactedBecause: @Contextual Event<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("prev_content") val previousContent: C? = null,
        @SerialName("m.relations") override val aggregations: Aggregations? = null,
    ) : UnsignedRoomEventData
}