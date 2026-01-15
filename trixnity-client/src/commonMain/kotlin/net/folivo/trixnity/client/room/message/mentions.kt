package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Mentions

fun MessageBuilder.mentionsRoom() = mentions(room = true)
fun MessageBuilder.mentionsUser(user: UserId) = mentions(setOf(user))

/**
 * Add mentions to the message. Can be called multiple times and adds new mentions each time.
 */
fun MessageBuilder.mentions(
    users: Set<UserId>? = mentions?.users,
    room: Boolean? = mentions?.room,
) {
    mentions = (mentions ?: Mentions()) + Mentions(users, room)
}