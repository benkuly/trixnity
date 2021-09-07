package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.UnknownEncryptedEventContent

fun createEncryptedEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(EncryptedEventContentSerializer)
        contextual(OlmEncryptedEventContentSerializer)
        contextual(MegolmEncryptedEventContentSerializer)
        contextual(UnknownEncryptedEventContent.serializer())
    }
}