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
import net.folivo.trixnity.core.model.events.AccountDataEventContent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnknownAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class AccountDataEventSerializer(
    private val messageEventContentSerializers: Set<EventContentSerializerMapping<out AccountDataEventContent>>,
    loggerFactory: LoggerFactory
) : KSerializer<Event.AccountDataEvent<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomEventSerializer")

    private val eventsContentLookupByType = messageEventContentSerializers.associate { it.type to it.serializer }

    override fun deserialize(decoder: Decoder): Event.AccountDataEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventsContentLookupByType[type]
            ?: UnknownEventContentSerializer(UnknownAccountDataEventContent.serializer(), type)
        return try {
            decoder.json.decodeFromJsonElement(Event.AccountDataEvent.serializer(contentSerializer), jsonObj)
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(
                Event.AccountDataEvent.serializer(
                    UnknownEventContentSerializer(
                        UnknownAccountDataEventContent.serializer(),
                        type
                    )
                ), jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Event.AccountDataEvent<*>) {
        val content = value.content
        val type: String
        val serializer: KSerializer<out AccountDataEventContent>

        when(content) {
            is UnknownAccountDataEventContent -> {
                type = content.eventType
                serializer = UnknownEventContentSerializer(UnknownAccountDataEventContent.serializer(), type)
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
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            (AddFieldsSerializer(
                Event.AccountDataEvent.serializer(serializer) as KSerializer<Event.AccountDataEvent<*>>,
                "type" to type
            )), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}