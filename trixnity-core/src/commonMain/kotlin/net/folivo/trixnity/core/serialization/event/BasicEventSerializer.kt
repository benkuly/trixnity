package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.Event.BasicEvent
import net.folivo.trixnity.core.model.events.UnknownBasicEventContent
import net.folivo.trixnity.core.serialization.HideDiscriminatorSerializer

class BasicEventSerializer : KSerializer<BasicEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BasicEventSerializer")

    override fun deserialize(decoder: Decoder): BasicEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = UnknownEventContentSerializer(UnknownBasicEventContent.serializer(), type)
        return decoder.json.decodeFromJsonElement(
            HideDiscriminatorSerializer(
                BasicEvent.serializer(contentSerializer),
                "type",
                type
            ), jsonObj
        )
    }

    override fun serialize(encoder: Encoder, value: BasicEvent<*>) {
        throw IllegalArgumentException("BasicEvent should never be serialized")
    }
}