package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.m.secretstorage.AesHmacSha2KeyEventContentSerializer

fun createSecretKeyEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(AesHmacSha2KeyEventContentSerializer)
    }
}