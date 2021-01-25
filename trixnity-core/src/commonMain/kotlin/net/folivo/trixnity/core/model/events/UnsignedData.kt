package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnsignedData(
    @SerialName("age") val age: Long? = null,
    @SerialName("redactedBecause") val redactedBecause: Event<*>? = null,
    @SerialName("transaction_id") val transactionId: String? = null
)