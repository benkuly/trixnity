package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent

val Event<*>.isEncrypted
    get() = content is EncryptedEventContent