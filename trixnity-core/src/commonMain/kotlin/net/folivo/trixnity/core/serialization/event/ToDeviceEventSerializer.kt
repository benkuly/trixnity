package net.folivo.trixnity.core.serialization.event

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
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.UnknownToDeviceEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class ToDeviceEventSerializer(
    private val toDeviceEventContentSerializers: Set<EventContentSerializerMapping<out ToDeviceEventContent>>,
    loggerFactory: LoggerFactory
) : KSerializer<ToDeviceEvent<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ToDeviceEventSerializer")

    private val eventsContentLookupByType = toDeviceEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): ToDeviceEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventsContentLookupByType[type]
            ?: UnknownEventContentSerializer(UnknownToDeviceEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(
                ToDeviceEvent.serializer(contentSerializer), jsonObj
            )
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                ToDeviceEvent.serializer(UnknownEventContentSerializer(UnknownToDeviceEventContent.serializer(), type)),
                jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: ToDeviceEvent<*>) {
        val content = value.content
        if (content is UnknownToDeviceEventContent) throw IllegalArgumentException("${content::class.simpleName} should never be serialized")
        require(encoder is JsonEncoder)
        val contentSerializerMapping = toDeviceEventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentSerializerMapping) { "event content type ${value.content::class} must be registered" }

        val addFields = mutableListOf("type" to contentSerializerMapping.type)
        val contentSerializer = contentSerializerMapping.serializer

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                ToDeviceEvent.serializer(contentSerializer) as KSerializer<ToDeviceEvent<*>>,
                *addFields.toTypedArray()
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}