package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface UnsignedRoomEventData {
    val age: Long?
    val redactedBecause: Event<*>?

    @SerialName("transaction_id")
    val transactionId: String?

    @Serializable
    data class UnsignedMessageEventData(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redactedBecause") @Contextual override val redactedBecause: Event<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null
    ) : UnsignedRoomEventData

    @Serializable
    data class UnsignedStateEventData<C : StateEventContent>(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redactedBecause") @Contextual override val redactedBecause: Event<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("prev_content") val previousContent: C? = null
    ) : UnsignedRoomEventData
}