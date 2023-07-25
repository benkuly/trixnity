package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.Relations

sealed interface UnsignedRoomEventData {
    val age: Long?
    val redactedBecause: Event<*>?
    val transactionId: String?
    val relations: Relations?

    @Serializable
    data class UnsignedMessageEventData(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redacted_because") override val redactedBecause: @Contextual Event<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("m.relations") override val relations: Relations? = null,
    ) : UnsignedRoomEventData

    @Serializable
    data class UnsignedStateEventData<C : StateEventContent>(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redacted_because") override val redactedBecause: @Contextual Event<*>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("prev_content") val previousContent: C? = null,
        @SerialName("m.relations") override val relations: Relations? = null,
    ) : UnsignedRoomEventData
}