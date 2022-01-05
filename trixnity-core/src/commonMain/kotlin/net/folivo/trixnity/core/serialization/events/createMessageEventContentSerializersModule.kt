package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.m.room.*

fun createMessageEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(RoomMessageEventContentSerializer)
        contextual(TextMessageEventContentSerializer)
        contextual(NoticeMessageEventContentSerializer)
        contextual(ImageMessageEventContentSerializer)
        contextual(FileMessageEventContentSerializer)
        contextual(AudioMessageEventContentSerializer)
        contextual(VideoMessageEventContentSerializer)
        contextual(VerificationRequestMessageEventContentSerializer)
    }
}