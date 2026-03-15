@file:OptIn(de.connect2x.trixnity.core.MSC4143::class)

package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.overwriteWith

@MSC4143
class RtcSlotEventContentSerializer(
    rtcApplicationSerializerMappings: RtcApplicationSerializerMappings = RtcApplicationSerializerMappings.default,
) : KSerializer<RtcSlotEventContent> {

    private val applicationSerializer = RtcApplicationSerializer(rtcApplicationSerializerMappings)
    private val delegate = RtcSlotEventContent.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    private fun jsonWithModule(baseJson: Json): Json = Json(baseJson) {
        serializersModule = baseJson.serializersModule.overwriteWith(
            SerializersModule { contextual(applicationSerializer) }
        )
    }

    override fun deserialize(decoder: Decoder): RtcSlotEventContent {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement().jsonObject
        return jsonWithModule(decoder.json).decodeFromJsonElement(delegate, element)
    }

    override fun serialize(encoder: Encoder, value: RtcSlotEventContent) {
        require(encoder is JsonEncoder)
        val element = jsonWithModule(encoder.json).encodeToJsonElement(delegate, value)
        encoder.encodeJsonElement(element)
    }
}
