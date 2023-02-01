package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent

val Event<*>.isEncrypted: Boolean
    get() = content is EncryptedEventContent

val Room.hasBeenReplaced: Boolean
    get() = nextRoomId != null