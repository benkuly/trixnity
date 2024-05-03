package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Mentions

fun MessageBuilder.mentionsRoom() = mentions(room = true)
fun MessageBuilder.mentions(vararg users: UserId, room: Boolean? = mentions?.room) = mentions(users.toSet(), room)

/**
 * Add mentions to the message. Can be called multiple times and adds new mentions each time.
 */
fun MessageBuilder.mentions(
    users: Set<UserId>? = null,
    room: Boolean? = mentions?.room,
) {
    mentions += Mentions(users, room)
}