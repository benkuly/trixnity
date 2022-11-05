package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Contextual
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
sealed interface RelatesTo { // TODO could be generic like Aggregation
    val relationType: RelationType?
    val eventId: EventId?

    @Serializable
    data class Reference(
        @SerialName("event_id")
        override val eventId: EventId,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val relationType: RelationType = RelationType.Reference
    }

    @Serializable
    data class Replace(
        @SerialName("event_id")
        override val eventId: EventId,
        /**
         * The content used to replace the referenced event.
         * This can be null, because it is not present in encrypted events.
         */
        @SerialName("m.new_content")
        val newContent: @Contextual MessageEventContent?,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val relationType: RelationType = RelationType.Replace
    }

    data class Unknown(
        val raw: JsonObject,
        override val eventId: EventId?,
        override val relationType: RelationType?,
    ) : RelatesTo
}

object RelatesToSerializer : KSerializer<RelatesTo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelatesToSerializer")

    override fun deserialize(decoder: Decoder): RelatesTo {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val relationType: RelationType? =
            jsonObject["rel_type"]?.jsonPrimitive?.let { decoder.json.decodeFromJsonElement(it) }
        return try {
            when (relationType) {
                is RelationType.Reference -> decoder.json.decodeFromJsonElement<RelatesTo.Reference>(jsonObject)
                is RelationType.Replace -> decoder.json.decodeFromJsonElement<RelatesTo.Replace>(jsonObject)
                else -> {
                    RelatesTo.Unknown(
                        jsonObject,
                        jsonObject["event_id"]?.jsonPrimitive?.content?.let { EventId(it) },
                        relationType
                    )
                }
            }
        } catch (e: Exception) {
            RelatesTo.Unknown(
                jsonObject,
                jsonObject["event_id"]?.jsonPrimitive?.content?.let { EventId(it) },
                relationType
            )
        }
    }

    override fun serialize(encoder: Encoder, value: RelatesTo) {
        require(encoder is JsonEncoder)
        val jsonObject = when (value) {
            is RelatesTo.Reference -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Replace -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonObject)
    }
}