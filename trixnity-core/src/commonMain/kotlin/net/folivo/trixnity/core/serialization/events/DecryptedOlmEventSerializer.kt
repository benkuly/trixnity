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
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class DecryptedOlmEventSerializer(
    private val eventContentSerializers: Set<SerializerMapping<out EventContent>>,
) : KSerializer<DecryptedOlmEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DecryptedOlmEventSerializer")

    override fun deserialize(decoder: Decoder): DecryptedOlmEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val contentSerializer = eventContentSerializers.contentDeserializer(type)
        return decoder.json.tryDeserializeOrElse(DecryptedOlmEvent.serializer(contentSerializer), jsonObj) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            DecryptedOlmEvent.serializer(UnknownEventContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: DecryptedOlmEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = eventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                DecryptedOlmEvent.serializer(serializer) as KSerializer<DecryptedOlmEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}