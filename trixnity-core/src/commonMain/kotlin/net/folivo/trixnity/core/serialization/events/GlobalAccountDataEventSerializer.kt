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
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.HideFieldsSerializer

private val log = KotlinLogging.logger {}

class GlobalAccountDataEventSerializer(
    private val messageEventContentSerializers: Set<EventContentSerializerMapping<out GlobalAccountDataEventContent>>,
) : KSerializer<GlobalAccountDataEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GlobalAccountDataEventSerializer")

    private val eventsContentLookupByType = messageEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): GlobalAccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val mapping = eventsContentLookupByType.entries.find { type.startsWith(it.key) }
        val contentSerializer = mapping?.value
            ?: UnknownEventContentSerializer(UnknownGlobalAccountDataEventContent.serializer(), type)
        return try {
            val key = if (mapping != null && mapping.key != type) type.substringAfter(mapping.key) else ""
            decoder.json.decodeFromJsonElement(
                AddFieldsSerializer(
                    GlobalAccountDataEvent.serializer(contentSerializer),
                    "key" to key
                ), jsonObj
            )
        } catch (error: SerializationException) {
            log.warn(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                GlobalAccountDataEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownGlobalAccountDataEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: GlobalAccountDataEvent<*>) {
        val content = value.content
        val type: String
        val serializer: KSerializer<out GlobalAccountDataEventContent>

        when (content) {
            is UnknownGlobalAccountDataEventContent -> {
                type = content.eventType
                serializer = UnknownEventContentSerializer(UnknownGlobalAccountDataEventContent.serializer(), type)
            }
            else -> {
                val contentDescriptor = messageEventContentSerializers.find { it.kClass.isInstance(content) }
                requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
                type = contentDescriptor.type
                serializer = contentDescriptor.serializer
            }
        }
        require(encoder is JsonEncoder)
        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            (HideFieldsSerializer(
                AddFieldsSerializer(
                    Event.GlobalAccountDataEvent.serializer(serializer) as KSerializer<GlobalAccountDataEvent<*>>,
                    "type" to type + value.key
                ), "key"
            )), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}