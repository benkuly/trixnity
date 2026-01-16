package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.room.firstWithContent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.Mentions
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