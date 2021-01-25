package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.DefaultMessageEventContent

fun createMessageEventContentSerializersModule(): SerializersModule {
    return SerializersModule {
        contextual(MessageEventContentSerializer)
        contextual(TextMessageEventContentSerializer)
        contextual(NoticeMessageEventContentSerializer)
        contextual(DefaultMessageEventContent.serializer())

        polymorphic(MessageEventContent::class, MessageEventContentSerializer) {
            subclass(TextMessageEventContentSerializer)
            subclass(NoticeMessageEventContentSerializer)
            subclass(DefaultMessageEventContent.serializer())
        }
    }
}