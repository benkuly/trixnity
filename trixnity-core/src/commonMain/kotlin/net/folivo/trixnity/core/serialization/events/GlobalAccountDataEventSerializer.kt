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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger("net.folivo.trixnity.core.serialization.events.GlobalAccountDataEventSerializer")

class GlobalAccountDataEventSerializer(
    globalAccountDataEventContentSerializers: Set<EventContentSerializerMapping<GlobalAccountDataEventContent>>,
) : KSerializer<GlobalAccountDataEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GlobalAccountDataEventSerializer")
    private val mappings = EventContentToEventSerializerMappings(
        baseMapping = globalAccountDataEventContentSerializers,
        eventDeserializer = { GlobalAccountDataEvent.serializer(it.serializer) },
        unknownEventSerializer = { GlobalAccountDataEvent.serializer(UnknownEventContentSerializer(it)) },
        typeField = null,
    )

    private val wildcardMappings = globalAccountDataEventContentSerializers.filter { it.type.endsWith("*") }

    override fun deserialize(decoder: Decoder): GlobalAccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = (jsonObj["type"] as? JsonPrimitive)?.content ?: throw SerializationException("type must not be null")
        val mappingType = wildcardMappings.find { type.startsWith(it.type.removeSuffix("*")) }?.type
        val baseSerializer = mappings[mappingType ?: type]
        val key =
            if (mappingType != null && mappingType != type) type.substringAfter(mappingType.removeSuffix("*")) else ""
        return decoder.json.tryDeserializeOrElse(
            AddFieldsSerializer(
                baseSerializer,
                "key" to key
            ), jsonObj
        ) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            @Suppress("UNCHECKED_CAST")
            GlobalAccountDataEvent.serializer(UnknownEventContentSerializer(type)) as KSerializer<GlobalAccountDataEvent<GlobalAccountDataEventContent>>
        }
    }

    override fun serialize(encoder: Encoder, value: GlobalAccountDataEvent<*>) {
        require(encoder is JsonEncoder)
        val (type, baseSerializer) = mappings[value.content]
        val jsonElement = encoder.json.encodeToJsonElement(
            (HideFieldsSerializer(
                AddFieldsSerializer(
                    @Suppress("UNCHECKED_CAST") (baseSerializer as KSerializer<GlobalAccountDataEvent<*>>),
                    "type" to type.removeSuffix("*") + value.key
                ), "key"
            )), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}