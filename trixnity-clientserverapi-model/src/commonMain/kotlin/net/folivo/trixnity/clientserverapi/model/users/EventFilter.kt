package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventFilter(
    @SerialName("limit") val limit: Int? = null,
    @SerialName("not_senders") val notSenders: Set<String>? = null,
    @SerialName("not_types") val notTypes: Set<String>? = null,
    @SerialName("senders") val senders: Set<String>? = null,
    @SerialName("types") val types: Set<String>? = null
)