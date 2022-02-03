package net.folivo.trixnity.core.model.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId

@Serializable(with = RelatesToSerializer::class)
sealed class RelatesTo {

    @Serializable
    data class Reference(
        @SerialName("event_id")
        val eventId: EventId,
        @SerialName("rel_type")
        val type: String = "m.reference"
    ) : RelatesTo() {
        companion object {
            const val type = "m.reference"
        }
    }

    data class Unknown(
        val raw: JsonObject
    ) : RelatesTo()
}

object RelatesToSerializer : KSerializer<RelatesTo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelatesToSerializer")

    override fun deserialize(decoder: Decoder): RelatesTo {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return when (jsonObject["rel_type"]?.jsonPrimitive?.content) {
            RelatesTo.Reference.type -> decoder.json.decodeFromJsonElement<RelatesTo.Reference>(jsonObject)
            else -> {
                RelatesTo.Unknown(jsonObject)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: RelatesTo) {
        require(encoder is JsonEncoder)
        val (jsonObject, type) = when (value) {
            is RelatesTo.Reference -> encoder.json.encodeToJsonElement(value) to RelatesTo.Reference.type
            is RelatesTo.Unknown -> value.raw to null
        }
        encoder.encodeJsonElement(JsonObject(buildMap {
            putAll(jsonObject.jsonObject)
            if (type != null) put("rel_type", JsonPrimitive(type))
        }))
    }
}