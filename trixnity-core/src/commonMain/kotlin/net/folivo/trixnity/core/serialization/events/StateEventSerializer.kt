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
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

class StateEventSerializer(
    private val stateEventContentSerializers: Set<SerializerMapping<out StateEventContent>>,
    private val stateEventContentSerializer: StateEventContentSerializer,
) : KSerializer<StateEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StateEventSerializer")

    override fun deserialize(decoder: Decoder): StateEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")
        val contentSerializer = StateEventContentSerializer(stateEventContentSerializers, type)
        return decoder.json.decodeFromJsonElement(StateEvent.serializer(contentSerializer), jsonObj)
    }

    override fun serialize(encoder: Encoder, value: StateEvent<*>) {
        require(encoder is JsonEncoder)
        val type = stateEventContentSerializers.contentType(value.content)
        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                StateEvent.serializer(stateEventContentSerializer) as KSerializer<StateEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}