package de.connect2x.trixnity.core.serialization.events

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.*

private val log = Logger("de.connect2x.trixnity.core.serialization.events.PersistentDataUnit")

class PersistentDataUnitSerializer(
    private val persistentMessageDataUnitSerializer: KSerializer<PersistentMessageDataUnit<*>>,
    private val persistentStateDataUnitSerializer: KSerializer<PersistentStateDataUnit<*>>,
) : KSerializer<PersistentDataUnit<*>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RoomEvent")

    override fun deserialize(decoder: Decoder): PersistentDataUnit<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val hasStateKey = "state_key" in jsonObj
        val serializer = if (hasStateKey) persistentStateDataUnitSerializer else persistentMessageDataUnitSerializer
        return decoder.json.tryDeserializeOrElse(serializer, jsonObj) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            UnknownPersistentDataUnitSerializer
        }
    }

    override fun serialize(encoder: Encoder, value: PersistentDataUnit<*>) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is PersistentMessageDataUnit -> encoder.json.encodeToJsonElement(persistentMessageDataUnitSerializer, value)
            is PersistentStateDataUnit -> encoder.json.encodeToJsonElement(persistentStateDataUnitSerializer, value)
            is UnknownPersistentDataUnit -> encoder.json.encodeToJsonElement(UnknownPersistentDataUnitSerializer, value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}