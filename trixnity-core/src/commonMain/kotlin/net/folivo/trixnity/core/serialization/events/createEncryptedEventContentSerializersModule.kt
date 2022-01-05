package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.UnknownEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContentSerializer
import net.folivo.trixnity.core.model.events.m.room.MegolmEncryptedEventContentSerializer
import net.folivo.trixnity.core.model.events.m.room.OlmEncryptedEventContentSerializer

fun createEncryptedEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(EncryptedEventContentSerializer)
        contextual(OlmEncryptedEventContentSerializer)
        contextual(MegolmEncryptedEventContentSerializer)
        contextual(UnknownEncryptedEventContent.serializer())
    }
}