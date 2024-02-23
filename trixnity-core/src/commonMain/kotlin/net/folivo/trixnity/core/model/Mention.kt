package net.folivo.trixnity.core.model

sealed interface Mention {
    val full: String
    val localpart: String
    val domain: String
}

val a = UserId
val b = RoomId
val c = RoomAliasId
val d = EventId
