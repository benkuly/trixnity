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
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnknownRoomEventContent
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class RoomEventSerializer(
    private val messageEventSerializer: KSerializer<MessageEvent<*>>,
    private val stateEventSerializer: KSerializer<StateEvent<*>>,
    loggerFactory: LoggerFactory
) : KSerializer<RoomEvent<*>> {
    private val log = newLogger(loggerFactory)
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RoomEventSerializer")

    override fun deserialize(decoder: Decoder): RoomEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val hasStateKey = "state_key" in jsonObj
        val serializer = if (hasStateKey) stateEventSerializer else messageEventSerializer
        return try {
            decoder.json.decodeFromJsonElement(serializer, jsonObj)
        } catch (error: SerializationException) {
            log.warning(error) { "could not deserialize event" }
            val type = jsonObj["type"]?.jsonPrimitive?.content
            requireNotNull(type)
            decoder.json.decodeFromJsonElement(
                StateEvent.serializer(UnknownEventContentSerializer(UnknownRoomEventContent.serializer(), type)),
                jsonObj
            )
        }
    }

    override fun serialize(encoder: Encoder, value: RoomEvent<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is MessageEvent -> encoder.json.encodeToJsonElement(messageEventSerializer, value)
            is StateEvent -> encoder.json.encodeToJsonElement(stateEventSerializer, value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}