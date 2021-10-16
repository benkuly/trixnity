package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

object RoomMessageEventContentSerializer : KSerializer<RoomMessageEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomMessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val type = jsonObj["msgtype"]?.jsonPrimitive?.content) {
            NoticeMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(NoticeMessageEventContentSerializer, jsonObj)
            TextMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(TextMessageEventContentSerializer, jsonObj)
            EmoteMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(EmoteMessageEventContentSerializer, jsonObj)
            ImageMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(ImageMessageEventContentSerializer, jsonObj)
            FileMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(FileMessageEventContentSerializer, jsonObj)
            AudioMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(AudioMessageEventContentSerializer, jsonObj)
            VideoMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(VideoMessageEventContentSerializer, jsonObj)
            else -> {
                val body = jsonObj["body"]?.jsonPrimitive?.content
                requireNotNull(type)
                requireNotNull(body)
                UnknownMessageEventContent(type, body, jsonObj)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: RoomMessageEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is NoticeMessageEventContent ->
                encoder.json.encodeToJsonElement(NoticeMessageEventContentSerializer, value)
            is TextMessageEventContent ->
                encoder.json.encodeToJsonElement(TextMessageEventContentSerializer, value)
            is EmoteMessageEventContent ->
                encoder.json.encodeToJsonElement(EmoteMessageEventContentSerializer, value)
            is ImageMessageEventContent ->
                encoder.json.encodeToJsonElement(ImageMessageEventContentSerializer, value)
            is FileMessageEventContent ->
                encoder.json.encodeToJsonElement(FileMessageEventContentSerializer, value)
            is AudioMessageEventContent ->
                encoder.json.encodeToJsonElement(AudioMessageEventContentSerializer, value)
            is VideoMessageEventContent ->
                encoder.json.encodeToJsonElement(VideoMessageEventContentSerializer, value)
            is UnknownMessageEventContent -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object NoticeMessageEventContentSerializer :
    AddFieldsSerializer<NoticeMessageEventContent>(
        NoticeMessageEventContent.serializer(),
        "msgtype" to NoticeMessageEventContent.type
    )

object TextMessageEventContentSerializer :
    AddFieldsSerializer<TextMessageEventContent>(
        TextMessageEventContent.serializer(),
        "msgtype" to TextMessageEventContent.type
    )

object EmoteMessageEventContentSerializer :
    AddFieldsSerializer<EmoteMessageEventContent>(
        EmoteMessageEventContent.serializer(),
        "msgtype" to EmoteMessageEventContent.type
    )

object ImageMessageEventContentSerializer :
    AddFieldsSerializer<ImageMessageEventContent>(
        ImageMessageEventContent.serializer(),
        "msgtype" to ImageMessageEventContent.type
    )

object FileMessageEventContentSerializer :
    AddFieldsSerializer<FileMessageEventContent>(
        FileMessageEventContent.serializer(),
        "msgtype" to FileMessageEventContent.type
    )

object AudioMessageEventContentSerializer :
    AddFieldsSerializer<AudioMessageEventContent>(
        AudioMessageEventContent.serializer(),
        "msgtype" to AudioMessageEventContent.type
    )

object VideoMessageEventContentSerializer :
    AddFieldsSerializer<VideoMessageEventContent>(
        VideoMessageEventContent.serializer(),
        "msgtype" to VideoMessageEventContent.type
    )