package net.folivo.trixnity.core.model

sealed interface Mention {
    data class User(val userId: UserId) : Mention
    data class Room(val roomId: RoomId) : Mention
    data class RoomEvent(val eventId: EventId, val roomId: RoomId) : Mention
    data class RoomAlias(val roomAliasId: RoomAliasId) : Mention
    data class RoomAliasEvent(val eventId: EventId, val roomAliasId: RoomAliasId) : Mention
}