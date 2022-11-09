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
import net.folivo.trixnity.core.model.events.Event.InitialStateEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class InitialStateEventSerializer(
    private val stateEventContentSerializers: Set<SerializerMapping<out StateEventContent>>,
    private val stateEventContentSerializer: StateEventContentSerializer,
) : KSerializer<InitialStateEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InitialStateEventSerializer")

    override fun deserialize(decoder: Decoder): InitialStateEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val isFullyRedacted = jsonObj["content"]?.jsonObject?.isEmpty() == true
        val contentSerializer =
            StateEventContentSerializer.withRedaction(stateEventContentSerializers, type, isFullyRedacted)
        return decoder.json.tryDeserializeOrElse(InitialStateEvent.serializer(contentSerializer), jsonObj) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            InitialStateEvent.serializer(UnknownStateEventContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: InitialStateEvent<*>) {
        require(encoder is JsonEncoder)
        val type = stateEventContentSerializers.contentType(value.content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                InitialStateEvent.serializer(stateEventContentSerializer) as KSerializer<InitialStateEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}