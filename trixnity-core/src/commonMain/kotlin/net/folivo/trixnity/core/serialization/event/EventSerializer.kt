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
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class EventSerializer(
    private val basicEventSerializer: KSerializer<BasicEvent<*>>,
    private val roomEventSerializer: KSerializer<RoomEvent<*>>,
    private val strippedStateEventSerializer: KSerializer<StrippedStateEvent<*>>,
    private val ephemeralEventSerializer: KSerializer<EphemeralEvent<*>>,
    private val toDeviceEventSerializer: KSerializer<ToDeviceEvent<*>>,
    private val olmEventSerializer: KSerializer<OlmEvent<*>>,
    private val megolmEventSerializer: KSerializer<MegolmEvent<*>>,
    private val globalAccountDataEventSerializer: KSerializer<GlobalAccountDataEvent<*>>,
    private val roomAccountDataEventSerializer: KSerializer<RoomAccountDataEvent<*>>,
    loggerFactory: LoggerFactory
) : KSerializer<Event<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("EventSerializer")

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
            // it is hard to detect if an event is e. g. an MegolmEvent or an EphemeralEvent and we don't need it
            else -> basicEventSerializer
        }
        return try {
            decoder.json.decodeFromJsonElement(serializer, jsonObj)
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            decoder.json.decodeFromJsonElement(basicEventSerializer, jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: Event<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is RoomEvent -> encoder.json.encodeToJsonElement(roomEventSerializer, value)
            is StrippedStateEvent -> encoder.json.encodeToJsonElement(strippedStateEventSerializer, value)
            is EphemeralEvent -> encoder.json.encodeToJsonElement(ephemeralEventSerializer, value)
            is ToDeviceEvent -> encoder.json.encodeToJsonElement(toDeviceEventSerializer, value)
            is OlmEvent -> encoder.json.encodeToJsonElement(olmEventSerializer, value)
            is MegolmEvent -> encoder.json.encodeToJsonElement(megolmEventSerializer, value)
            is GlobalAccountDataEvent -> encoder.json.encodeToJsonElement(globalAccountDataEventSerializer, value)
            is RoomAccountDataEvent -> encoder.json.encodeToJsonElement(roomAccountDataEventSerializer, value)
            is BasicEvent<*> -> encoder.json.encodeToJsonElement(basicEventSerializer, value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}