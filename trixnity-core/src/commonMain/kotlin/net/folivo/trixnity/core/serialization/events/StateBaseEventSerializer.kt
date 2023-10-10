package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.serialization.canonicalJson

class StateBaseEventSerializer(
    private val stateEventSerializer: KSerializer<StateEvent<*>>,
    private val strippedStateEventSerializer: KSerializer<StrippedStateEvent<*>>,
) : KSerializer<StateBaseEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StateBaseEventSerializer")

    override fun deserialize(decoder: Decoder): StateBaseEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val hasEventId = "event_id" in jsonObj
        val serializer = if (hasEventId) stateEventSerializer else strippedStateEventSerializer
        return decoder.json.decodeFromJsonElement(serializer, jsonObj)
    }

    override fun serialize(encoder: Encoder, value: StateBaseEvent<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is StateEvent -> encoder.json.encodeToJsonElement(stateEventSerializer, value)
            is StrippedStateEvent -> encoder.json.encodeToJsonElement(strippedStateEventSerializer, value)
        }
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}