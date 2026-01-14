package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.EmptyEventContent
import net.folivo.trixnity.core.model.events.PersistentDataUnit.UnknownPersistentDataUnit
import net.folivo.trixnity.core.serialization.canonicalJson

object UnknownPersistentDataUnitSerializer : KSerializer<UnknownPersistentDataUnit> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownPersistentDataUnitSerializer")

    override fun deserialize(decoder: Decoder): UnknownPersistentDataUnit {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type =
            (jsonObj["type"] as? JsonPrimitive)?.contentOrNull ?: throw SerializationException("type must not be null")

        return UnknownPersistentDataUnit(EmptyEventContent, type, jsonObj)
    }

    override fun serialize(encoder: Encoder, value: UnknownPersistentDataUnit) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(value.raw))
    }
}