package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.model.events.DelayedEvent
import de.connect2x.trixnity.core.serialization.canonicalJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject

@MSC4140
class DelayedEventSerializer(
    private val messageEventSerializer: KSerializer<DelayedEvent.DelayedMessageEvent<*>>,
    private val stateEventSerializer: KSerializer<DelayedEvent.DelayedStateEvent<*>>,
) : KSerializer<DelayedEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DelayedEvent")

    override fun deserialize(decoder: Decoder): DelayedEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val hasStateKey = "state_key" in jsonObj
        val serializer = if (hasStateKey) stateEventSerializer else messageEventSerializer
        return decoder.json.decodeFromJsonElement(serializer, jsonObj)
    }

    override fun serialize(encoder: Encoder, value: DelayedEvent<*>) {
        require(encoder is JsonEncoder)
        val jsonElement =
            when (value) {
                is DelayedEvent.DelayedMessageEvent -> encoder.json.encodeToJsonElement(messageEventSerializer, value)
                is DelayedEvent.DelayedStateEvent -> encoder.json.encodeToJsonElement(stateEventSerializer, value)
            }
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}
