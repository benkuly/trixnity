package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent

val RoomEvent<*>.isEncrypted: Boolean
    get() = content is EncryptedEventContent

val Room.hasBeenReplaced: Boolean
    get() = nextRoomId != null