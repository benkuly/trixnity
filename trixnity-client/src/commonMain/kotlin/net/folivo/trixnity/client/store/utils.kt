package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent

operator fun MatrixId.plus(other: MatrixId): String {
    return "$this|$other"
}

val Event<*>.isEncrypted
    get() = content is EncryptedEventContent