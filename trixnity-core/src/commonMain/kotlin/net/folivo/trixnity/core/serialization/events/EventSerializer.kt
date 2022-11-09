package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*

private val log = KotlinLogging.logger {}

class EventSerializer(
    private val roomEventSerializer: KSerializer<RoomEvent<*>>,
    private val strippedStateEventSerializer: KSerializer<StrippedStateEvent<*>>,
    private val initialStateEventSerializer: KSerializer<InitialStateEvent<*>>,
    private val ephemeralEventSerializer: KSerializer<EphemeralEvent<*>>,
    private val toDeviceEventSerializer: KSerializer<ToDeviceEvent<*>>,
    private val globalAccountDataEventSerializer: KSerializer<GlobalAccountDataEvent<*>>,
    private val roomAccountDataEventSerializer: KSerializer<RoomAccountDataEvent<*>>,
) : KSerializer<Event<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EventSerializer")

    override fun deserialize(decoder: Decoder): Event<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val hasStateKey = "state_key" in jsonObj
        val hasEventId = "event_id" in jsonObj
        val hasRoomId = "room_id" in jsonObj
        val hasSenderId = "sender" in jsonObj
        val serializer = when {
            hasEventId && hasRoomId && hasSenderId -> roomEventSerializer
            !hasEventId && hasStateKey && hasRoomId && hasSenderId -> strippedStateEventSerializer
            !hasEventId && !hasStateKey && !hasRoomId && hasSenderId -> toDeviceEventSerializer
            // it is hard to detect if an event is e.g. an EphemeralEvent or RoomAccountDataEvent and we don't need it
            // -> that's why we skip some event types here.
            else -> UnknownEventSerializer
        }
        return decoder.json.tryDeserializeOrElse(serializer, jsonObj) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            UnknownEventSerializer
        }
    }

    override fun serialize(encoder: Encoder, value: Event<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is RoomEvent -> encoder.json.encodeToJsonElement(roomEventSerializer, value)
            is StrippedStateEvent -> encoder.json.encodeToJsonElement(strippedStateEventSerializer, value)
            is InitialStateEvent -> encoder.json.encodeToJsonElement(initialStateEventSerializer, value)
            is EphemeralEvent -> encoder.json.encodeToJsonElement(ephemeralEventSerializer, value)
            is ToDeviceEvent -> encoder.json.encodeToJsonElement(toDeviceEventSerializer, value)
            is GlobalAccountDataEvent -> encoder.json.encodeToJsonElement(globalAccountDataEventSerializer, value)
            is RoomAccountDataEvent -> encoder.json.encodeToJsonElement(roomAccountDataEventSerializer, value)
            is UnknownEvent -> encoder.json.encodeToJsonElement(UnknownEventSerializer, value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}