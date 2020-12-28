package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RoomEvent.UnsignedData

@Serializable
data class StandardUnsignedData(
    @SerialName("age") override val age: Long? = null,
    @SerialName("redactedBecause") override val redactedBecause: Event<@Polymorphic Any>? = null,
    @SerialName("transaction_id") override val transactionId: String? = null
) : UnsignedData