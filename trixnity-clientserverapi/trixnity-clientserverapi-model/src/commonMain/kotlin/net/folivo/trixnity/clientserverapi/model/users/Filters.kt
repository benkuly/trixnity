package net.folivo.trixnity.clientserverapi.model.users

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

    @Serializable
    data class EventFilter(
        @SerialName("limit") val limit: Int? = null,
        @SerialName("not_senders") val notSenders: Set<String>? = null,
        @SerialName("not_types") val notTypes: Set<String>? = null,
        @SerialName("senders") val senders: Set<String>? = null,
        @SerialName("types") val types: Set<String>? = null
    )

    @Serializable
    data class RoomFilter(
        @SerialName("not_rooms") val notRooms: Set<String>? = null,
        @SerialName("rooms") val rooms: Set<String>? = null,
        @SerialName("ephemeral") val ephemeral: RoomEventFilter? = null,
        @SerialName("state") val state: StateFilter? = null,
        @SerialName("timeline") val timeline: RoomEventFilter? = null,
        @SerialName("account_data") val accountData: RoomEventFilter? = null
    ) {
        @Serializable
        data class RoomEventFilter(
            @SerialName("limit") val limit: Int? = null,
            @SerialName("not_senders") val notSenders: Set<String>? = null,
            @SerialName("not_types") val notTypes: Set<String>? = null,
            @SerialName("senders") val senders: Set<String>? = null,
            @SerialName("types") val types: Set<String>? = null,
            @SerialName("lazy_load_members") val lazyLoadMembers: Boolean? = null,
            @SerialName("include_redundant_members") val includeRedundantMembers: Boolean? = null,
            @SerialName("not_rooms") val notRooms: Set<String>? = null,
            @SerialName("rooms") val rooms: Set<String>? = null,
            @SerialName("contains_url") val containsUrl: Boolean? = null
        )

        @Serializable
        data class StateFilter(
            @SerialName("limit") val limit: Int? = null,
            @SerialName("not_senders") val notSenders: Set<String>? = null,
            @SerialName("not_types") val notTypes: Set<String>? = null,
            @SerialName("senders") val senders: Set<String>? = null,
            @SerialName("types") val types: Set<String>? = null,
            @SerialName("lazy_load_members") val lazyLoadMembers: Boolean? = null,
            @SerialName("include_redundant_members") val includeRedundantMembers: Boolean? = null,
            @SerialName("not_rooms") val notRooms: Set<String>? = null,
            @SerialName("rooms") val rooms: Set<String>? = null,
            @SerialName("contains_url") val containsUrl: Boolean? = null
        )
    }
}