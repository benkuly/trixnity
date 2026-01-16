package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.replace

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
 * Returns true, when this is the first event of the room (not including room upgrades).
 */
val TimelineEvent.isFirst: Boolean
    get() = previousEventId == null && gap !is TimelineEvent.Gap.GapBefore && gap !is TimelineEvent.Gap.GapBoth

/**
 * Returns true, when this is the last known event of the room (not including room upgrades).
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
        if (event is MessageEvent) event.content.relatesTo
        else null