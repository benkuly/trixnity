package net.folivo.trixnity.core.model

sealed interface Mention {
    data class User(val userId: UserId) : Mention
    data class Room(val roomId: RoomId) : Mention
    data class RoomAlias(val roomAliasId: RoomAliasId) : Mention
    data class Event(val eventId: EventId, val room: Mention) : Mention
    data class Unknown(val nothing: Unit) : Mention
}
