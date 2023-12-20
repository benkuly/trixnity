package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent

val RoomEvent<*>.isEncrypted: Boolean
    get() = content is EncryptedMessageEventContent

val Room.hasBeenReplaced: Boolean
    get() = nextRoomId != null