package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.*

object MessageEventContentSerializer : KSerializer<MessageEventContent> {

    @InternalSerializationApi
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): MessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (jsonObj["msgtype"]?.jsonPrimitive?.content) {
            NoticeMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(NoticeMessageEventContentSerializer, jsonObj)
            TextMessageEventContent.type   ->
                decoder.json.decodeFromJsonElement(TextMessageEventContentSerializer, jsonObj)
            else                           ->
                decoder.json.decodeFromJsonElement(UnknownMessageEventContent.serializer(), jsonObj)
        }
    }

    @ExperimentalStdlibApi
    override fun serialize(encoder: Encoder, value: MessageEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is NoticeMessageEventContent  ->
                encoder.json.encodeToJsonElement(NoticeMessageEventContentSerializer, value)
            is TextMessageEventContent    ->
                encoder.json.encodeToJsonElement(TextMessageEventContentSerializer, value)
            is UnknownMessageEventContent ->
                encoder.json.encodeToJsonElement(UnknownMessageEventContent.serializer(), value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}