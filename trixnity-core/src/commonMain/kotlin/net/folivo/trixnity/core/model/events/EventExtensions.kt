package net.folivo.trixnity.core.model.events

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.*

val ClientEvent<*>.stateKeyOrNull: String?
    get() = when (this) {
        is StateBaseEvent -> this.stateKey
        else -> null
    }

val ClientEvent<*>.eventIdOrNull: EventId?
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