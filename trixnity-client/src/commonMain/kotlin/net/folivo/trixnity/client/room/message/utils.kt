package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.Mentions
import kotlin.time.Duration.Companion.seconds

internal suspend fun RoomService.getTimelineEventWithContentAndTimeout(roomId: RoomId, eventId: EventId) =
    getTimelineEvent(roomId, eventId) {
        decryptionTimeout = 5.seconds
        allowReplaceContent = false
    }.firstWithContent()

operator fun Mentions.plus(other: Mentions): Mentions {
    return Mentions(
        users = other.users.orEmpty() + users.orEmpty(),
        room = other.room ?: room
    )
}