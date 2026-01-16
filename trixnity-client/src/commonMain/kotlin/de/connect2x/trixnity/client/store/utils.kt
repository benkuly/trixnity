package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent

val RoomEvent<*>.isEncrypted: Boolean
    get() = content is EncryptedMessageEventContent

val Room.hasBeenReplaced: Boolean
    get() = nextRoomId != null