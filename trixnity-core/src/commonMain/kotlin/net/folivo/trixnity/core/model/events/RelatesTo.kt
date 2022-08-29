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
sealed interface RelatesTo {
    val type: RelationType
    val eventId: EventId

    @Serializable
    data class Reference(
        @SerialName("event_id")
        override val eventId: EventId,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val type: RelationType = RelationType.Reference
    }

    data class Unknown(
        val raw: JsonObject,
        override val eventId: EventId,
        override val type: RelationType,
    ) : RelatesTo
}

object RelatesToSerializer : KSerializer<RelatesTo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelatesToSerializer")

    override fun deserialize(decoder: Decoder): RelatesTo {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val relationType = RelationType.of(jsonObject["rel_type"]?.jsonPrimitive?.content ?: "UNKNOWN")
        return try {
            when (relationType) {
                is RelationType.Reference -> decoder.json.decodeFromJsonElement<RelatesTo.Reference>(jsonObject)
                else -> {
                    RelatesTo.Unknown(
                        jsonObject,
                        EventId(jsonObject["event_id"]?.jsonPrimitive?.content ?: "UNKNOWN"),
                        relationType
                    )
                }
            }
        } catch (e: Exception) {
            RelatesTo.Unknown(
                jsonObject,
                EventId(jsonObject["event_id"]?.jsonPrimitive?.content ?: "UNKNOWN"),
                relationType
            )
        }
    }

    override fun serialize(encoder: Encoder, value: RelatesTo) {
        require(encoder is JsonEncoder)
        val jsonObject = when (value) {
            is RelatesTo.Reference -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonObject)
    }
}