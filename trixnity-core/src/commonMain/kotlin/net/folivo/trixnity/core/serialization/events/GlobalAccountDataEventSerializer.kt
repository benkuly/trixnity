package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class GlobalAccountDataEventSerializer(
    private val globalAccountDataEventContentSerializers: Set<SerializerMapping<out GlobalAccountDataEventContent>>,
) : KSerializer<GlobalAccountDataEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GlobalAccountDataEventSerializer")

    override fun deserialize(decoder: Decoder): GlobalAccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val mappingType = globalAccountDataEventContentSerializers.find { type.startsWith(it.type) }?.type
        val contentSerializer = globalAccountDataEventContentSerializers.contentDeserializer(type)
        val key = if (mappingType != null && mappingType != type) type.substringAfter(mappingType) else ""
        return decoder.json.tryDeserializeOrElse(
            AddFieldsSerializer(
                GlobalAccountDataEvent.serializer(contentSerializer),
                "key" to key
            ), jsonObj
        ) {
            log.warn(it) { "could not deserialize event of type $type" }
            GlobalAccountDataEvent.serializer(UnknownGlobalAccountDataEventContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: GlobalAccountDataEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = globalAccountDataEventContentSerializers.contentSerializer(value.content)
        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            (HideFieldsSerializer(
                AddFieldsSerializer(
                    GlobalAccountDataEvent.serializer(serializer) as KSerializer<GlobalAccountDataEvent<*>>,
                    "type" to type + value.key
                ), "key"
            )), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}