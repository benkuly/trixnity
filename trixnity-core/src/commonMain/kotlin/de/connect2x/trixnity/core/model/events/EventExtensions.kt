package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.EphemeralEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent

val ClientEvent<*>.stateKeyOrNull: String?
    get() = when (this) {
        is StateBaseEvent -> this.stateKey
        else -> null
    }

val ClientEvent<*>.idOrNull: EventId?
    get() = when (this) {
        is RoomEvent -> this.id
        else -> null
    }

val ClientEvent<*>.originTimestampOrNull: Long?
    get() = when (this) {
        is RoomEvent -> this.originTimestamp
        else -> null
    }

val ClientEvent<*>.roomIdOrNull: RoomId?
    get() = when (this) {
        is RoomEvent -> this.roomId
        is StrippedStateEvent -> this.roomId
        is RoomAccountDataEvent -> this.roomId
        is EphemeralEvent -> this.roomId
        else -> null
    }

val ClientEvent<*>.senderOrNull: UserId?
    get() = when (this) {
        is RoomEvent -> this.sender
        is StrippedStateEvent -> this.sender
        is ToDeviceEvent -> this.sender
        is EphemeralEvent -> this.sender
        else -> null
    }

@OptIn(MSC4354::class)
fun <T : RoomEventContent> RoomEvent<*>.mergeContentOrNull(
    content: T
): RoomEvent<T>? =
    when (this) {
        is RoomEvent.MessageEvent<*> if content is MessageEventContent ->
            @Suppress("UNCHECKED_CAST")
            RoomEvent.MessageEvent(
                content = content,
                id = id,
                sender = sender,
                roomId = roomId,
                originTimestamp = originTimestamp,
                unsigned = unsigned,
                sticky = sticky,
            ) as RoomEvent<T>

        is RoomEvent.StateEvent<*> if content is StateEventContent ->
            @Suppress("UNCHECKED_CAST")
            RoomEvent.StateEvent(
                content = content,
                id = id,
                sender = sender,
                roomId = roomId,
                originTimestamp = originTimestamp,
                unsigned = unsigned,
                stateKey = stateKey,
                sticky = sticky,
            ) as RoomEvent<T>

        else -> null
    }
