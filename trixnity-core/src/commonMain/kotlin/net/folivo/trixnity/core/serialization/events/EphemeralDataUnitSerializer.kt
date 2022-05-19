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
import net.folivo.trixnity.core.model.events.EphemeralDataUnit
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

private val log = KotlinLogging.logger {}

class EphemeralDataUnitSerializer(
    private val ephemeralDataUnitContentSerializers: Set<SerializerMapping<out EphemeralDataUnitContent>>,
) : KSerializer<EphemeralDataUnit<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EphemeralDataUnitSerializer")

    override fun deserialize(decoder: Decoder): EphemeralDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["edu_type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = ephemeralDataUnitContentSerializers.contentDeserializer(type)
        return decoder.json.tryDeserializeOrElse(EphemeralDataUnit.serializer(contentSerializer), jsonObj) {
            log.warn(it) { "could not deserialize event of type $type" }
            EphemeralDataUnit.serializer(UnknownEphemeralDataUnitContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: EphemeralDataUnit<*>) {
        require(encoder is JsonEncoder)
        val (type, serializer) = ephemeralDataUnitContentSerializers.contentSerializer(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            (AddFieldsSerializer(
                EphemeralDataUnit.serializer(serializer) as KSerializer<EphemeralDataUnit<*>>,
                "type" to type
            )), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}