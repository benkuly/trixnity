package net.folivo.trixnity.core.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.*

class MessageEventContentSerializer :
    JsonContentPolymorphicSerializer<MessageEventContent>(MessageEventContent::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out MessageEventContent> {
        return when (element.jsonObject["msgtype"]?.jsonPrimitive?.content) {
            "m.notice" -> NoticeMessageEventContent.serializer()
            "m.text"   -> TextMessageEventContent.serializer()
            else       -> UnknownMessageEventContent.serializer()
        }
    }
}