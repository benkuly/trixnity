package net.folivo.trixnity.client

import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*

fun Event<*>?.getRoomIdAndStateKey(): Pair<RoomId?, String?> {
    return when (this) {
        is StateEvent -> this.roomId to this.stateKey
        is StrippedStateEvent -> this.roomId to this.stateKey
        else -> null to null
    }
}

fun Event<*>?.getEventId(): EventId? {
    return when (this) {
        is RoomEvent -> this.id
        is StateEvent -> this.id
        else -> null
    }
}

fun Event<*>?.getOriginTimestamp(): Long? {
    return when (this) {
        is RoomEvent -> this.originTimestamp
        is StateEvent -> this.originTimestamp
        else -> null
    }
}

fun Event<*>?.getRoomId(): RoomId? {
    return when (this) {
        is RoomEvent -> this.roomId
        is StateEvent -> this.roomId
        is StrippedStateEvent -> this.roomId
        is EphemeralEvent -> this.roomId
        is MegolmEvent -> this.roomId
        else -> null
    }
}