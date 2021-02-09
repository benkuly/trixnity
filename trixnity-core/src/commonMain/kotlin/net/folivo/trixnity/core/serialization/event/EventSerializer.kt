package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*

class EventSerializer(
    private val basicEventSerializer: KSerializer<BasicEvent<*>>,
    private val roomEventSerializer: KSerializer<RoomEvent<*>>,
    private val stateEventSerializer: KSerializer<StateEvent<*>>,
    private val strippedStateEventSerializer: KSerializer<StrippedStateEvent<*>>,
) : KSerializer<Event<*>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("EventSerializer")

    override fun deserialize(decoder: Decoder): Event<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val hasStateKey = "state_key" in jsonObj
        val hasEventId = "event_id" in jsonObj
        val hasRoomId = "room_id" in jsonObj
        val serializer = when {
            hasStateKey && hasEventId  -> stateEventSerializer
            hasStateKey && !hasEventId -> strippedStateEventSerializer
            hasRoomId                  -> roomEventSerializer
            else                       -> basicEventSerializer
        }
        return decoder.json.decodeFromJsonElement(serializer, jsonObj)
    }

    override fun serialize(encoder: Encoder, value: Event<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is RoomEvent<*>          -> encoder.json.encodeToJsonElement(roomEventSerializer, value)
            is StateEvent<*>         -> encoder.json.encodeToJsonElement(stateEventSerializer, value)
            is StrippedStateEvent<*> -> encoder.json.encodeToJsonElement(strippedStateEventSerializer, value)
            is BasicEvent            -> encoder.json.encodeToJsonElement(basicEventSerializer, value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}