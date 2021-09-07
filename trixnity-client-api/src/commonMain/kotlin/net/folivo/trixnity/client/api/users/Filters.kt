package net.folivo.trixnity.client.api.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Filters(
    @SerialName("event_fields") val eventFields: Set<String>? = null,
    @SerialName("event_format") val eventFormat: EventFormat? = null,
    @SerialName("presence") val presence: EventFilter? = null,
    @SerialName("account_data") val accountData: EventFilter? = null,
    @SerialName("room") val room: RoomFilter? = null
) {
    @Serializable
    enum class EventFormat {
        @SerialName("client")
        CLIENT,

        @SerialName("federation")
        FEDERATION
    }
}