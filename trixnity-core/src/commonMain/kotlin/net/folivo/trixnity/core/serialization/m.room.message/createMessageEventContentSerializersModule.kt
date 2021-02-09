package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.UnknownMessageEventContent

fun createMessageEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(MessageEventContentSerializer)
        contextual(TextMessageEventContentSerializer)
        contextual(NoticeMessageEventContentSerializer)
        contextual(UnknownMessageEventContent.serializer())

//        polymorphic(MessageEventContent::class, MessageEventContentSerializer) {
//            subclass(TextMessageEventContentSerializer)
//            subclass(NoticeMessageEventContentSerializer)
//            subclass(UnknownMessageEventContent.serializer())
//        }
    }
}