package net.folivo.trixnity.core.model

sealed interface Mention {
    data class User(val userId: UserId) : Mention
    data class Room(val roomId: RoomId) : Mention
    data class RoomEvent(val roomId: RoomId, val eventId: EventId) : Mention
    data class RoomAlias(val roomAliasId: RoomAliasId) : Mention
    data class RoomAliasEvent(val roomAliasId: RoomAliasId, val eventId: EventId) : Mention
    data class Event(val eventId: EventId) : Mention
}