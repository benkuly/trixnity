package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class ToDeviceEventSerializer(
    private val toDeviceEventContentSerializers: Set<SerializerMapping<out ToDeviceEventContent>>,
) : KSerializer<ToDeviceEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ToDeviceEventSerializer")

    override fun deserialize(decoder: Decoder): ToDeviceEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = toDeviceEventContentSerializers.contentDeserializer(type)
        return decoder.json.tryDeserializeOrElse(ToDeviceEvent.serializer(contentSerializer), jsonObj) {
            log.warn(it) { "could not deserialize event of type $type" }
            ToDeviceEvent.serializer(UnknownToDeviceEventContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: ToDeviceEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = toDeviceEventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                ToDeviceEvent.serializer(serializer) as KSerializer<ToDeviceEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}