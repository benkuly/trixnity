package net.folivo.trixnity.core.model.events

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

val Event<*>.stateKeyOrNull: String?
    get() = when (this) {
        is Event.StateEvent -> this.stateKey
        is Event.StrippedStateEvent -> this.stateKey
        else -> null
    }

val Event<*>.eventIdOrNull: EventId?
    get() = when (this) {
        is Event.RoomEvent -> this.id
        else -> null
    }

val Event<*>.originTimestampOrNull: Long?
    get() = when (this) {
        is Event.RoomEvent -> this.originTimestamp
        else -> null
    }

val Event<*>.roomIdOrNull: RoomId?
    get() = when (this) {
        is Event.RoomEvent -> this.roomId
        is Event.StrippedStateEvent -> this.roomId
        is Event.RoomAccountDataEvent -> this.roomId
        is Event.EphemeralEvent -> this.roomId
        else -> null
    }

val Event<*>.senderOrNull: UserId?
    get() = when (this) {
        is Event.RoomEvent -> this.sender
        is Event.StrippedStateEvent -> this.sender
        is Event.ToDeviceEvent -> this.sender
        is Event.EphemeralEvent -> this.sender
        else -> null
    }