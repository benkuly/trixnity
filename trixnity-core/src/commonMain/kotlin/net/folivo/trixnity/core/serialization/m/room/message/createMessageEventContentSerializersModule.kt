package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.UnknownMessageEventContent

fun createMessageEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(RoomMessageEventContentSerializer)
        contextual(TextMessageEventContentSerializer)
        contextual(NoticeMessageEventContentSerializer)
        contextual(ImageMessageEventContentSerializer)
        contextual(FileMessageEventContentSerializer)
        contextual(AudioMessageEventContentSerializer)
        contextual(VideoMessageEventContentSerializer)
        contextual(UnknownMessageEventContent.serializer())
    }
}