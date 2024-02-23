package net.folivo.trixnity.core.model

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

sealed interface Mention {
    val full: String
    val localpart: String
    val domain: String
}

val a = UserId
val b = RoomId
val c = RoomAliasId
val d = EventId
