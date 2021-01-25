package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
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
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.HideDiscriminatorSerializer

class StateEventSerializer(
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>,
) : KSerializer<StateEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StateEventSerializer")

    private val eventsContentLookupByType = stateEventContentSerializers.map { Pair(it.type, it.serializer) }.toMap()

    override fun deserialize(decoder: Decoder): StateEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        val contentSerializer = eventsContentLookupByType[type] ?: UnknownStateEventContent.serializer()
        return decoder.json.decodeFromJsonElement(
            HideDiscriminatorSerializer(
                StateEvent.serializer(contentSerializer),
                "type",
                type
            ), jsonObj
        )
    }

    @ExperimentalStdlibApi
    override fun serialize(encoder: Encoder, value: StateEvent<*>) {
        require(encoder is JsonEncoder)
        val contentDescriptor = stateEventContentSerializers.find { it.kClass.isInstance(value.content) }
        requireNotNull(contentDescriptor, { "event content type ${value.content::class} must be registered" })

        val jsonElement = encoder.json.encodeToJsonElement(
            HideDiscriminatorSerializer(
                StateEvent.serializer(contentDescriptor.serializer) as KSerializer<StateEvent<*>>,
                "type",
                contentDescriptor.type
            ), value
        )
        encoder.encodeJsonElement(jsonElement)
    }
}