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
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.HideDiscriminatorSerializer

class EventSerializer(
    private val roomEventSerializer: KSerializer<RoomEvent<*>>,
    private val stateEventSerializer: KSerializer<StateEvent<*>>,
    private val strippedStateEventSerializer: KSerializer<StrippedStateEvent<*>>,
    roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
    stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>
) : KSerializer<Event<*>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("EventSerializer")

    override fun deserialize(decoder: Decoder): Event<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        val serializer =
            if ("state_key" in jsonObj) {
                if ("event_id" in jsonObj) {
                    stateEventSerializer
                } else {
                    strippedStateEventSerializer
                }
            } else if ("room_id" in jsonObj) {
                roomEventSerializer
            } else {
                HideDiscriminatorSerializer(
                    UnknownEvent.serializer(),
                    "type",
                    type ?: "unknown"
                )
            }
        return decoder.json.decodeFromJsonElement(serializer, jsonObj)
    }

    private val eventsContentLookupByClass = (roomEventContentSerializers + stateEventContentSerializers)

    override fun serialize(encoder: Encoder, value: Event<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is RoomEvent          -> encoder.json.encodeToJsonElement(roomEventSerializer, value)
            is StateEvent         -> encoder.json.encodeToJsonElement(stateEventSerializer, value)
            is StrippedStateEvent -> encoder.json.encodeToJsonElement(strippedStateEventSerializer, value)
            is UnknownEvent       -> {
                val contentDescriptor = eventsContentLookupByClass.find { it.kClass.isInstance(value.content) }
                requireNotNull(contentDescriptor, { "event content type ${value.content::class} must be registered" })
                encoder.json.encodeToJsonElement(
                    HideDiscriminatorSerializer(
                        UnknownEvent.serializer(),
                        "type",
                        contentDescriptor.type
                    ), value
                )
            }
        }
        encoder.encodeJsonElement(jsonElement)
    }
}