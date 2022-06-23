package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent

val ClientEvent<*>.isEncrypted
    get() = content is EncryptedEventContent