package net.folivo.trixnity.core.serialization.events

import io.github.oshai.kotlinlogging.KotlinLogging
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
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.Event.EphemeralEvent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class EphemeralEventSerializer(
    private val ephemeralEventContentSerializers: Set<SerializerMapping<out EphemeralEventContent>>,
) : KSerializer<EphemeralEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EphemeralEventSerializer")

    override fun deserialize(decoder: Decoder): EphemeralEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val contentSerializer = EphemeralEventContentSerializer(type, ephemeralEventContentSerializers)
        return decoder.json.decodeFromJsonElement(EphemeralEvent.serializer(contentSerializer), jsonObj)
    }

    override fun serialize(encoder: Encoder, value: EphemeralEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = ephemeralEventContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                EphemeralEvent.serializer(serializer) as KSerializer<EphemeralEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}