package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.replace

val TimelineEvent.eventId: EventId
    get() = event.id

val TimelineEvent.roomId: RoomId
    get() = event.roomId

val TimelineEvent.sender: UserId
    get() = event.sender

val TimelineEvent.originTimestamp: Long
    get() = event.originTimestamp

val TimelineEvent.unsigned: UnsignedRoomEventData?
    get() = event.unsigned
val TimelineEvent.isEncrypted: Boolean
    get() = event.isEncrypted

/**
 * Returns true, when this is the first event of the room.
 */
val TimelineEvent.isFirst: Boolean
    get() = previousEventId == null && gap !is TimelineEvent.Gap.GapBefore && gap !is TimelineEvent.Gap.GapBoth

/**
 * Returns true, when this is the last known event of the room.
 */
val TimelineEvent.isLast: Boolean
    get() = nextEventId == null

val TimelineEvent.isReplaced: Boolean
    get() =
        if (event is MessageEvent) event.unsigned?.relations?.replace != null
        else false

val TimelineEvent.isReplacing: Boolean
    get() = relatesTo is RelatesTo.Replace

val TimelineEvent.relatesTo: RelatesTo?
    get() =
        if (event is MessageEvent) {
            val content = event.content
            if (content is MessageEventContent) {
                content.relatesTo
            } else null
        } else null