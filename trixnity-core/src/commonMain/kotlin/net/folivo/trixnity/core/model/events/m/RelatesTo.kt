package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.MessageEventContent

@Serializable(with = RelatesToSerializer::class)
sealed interface RelatesTo {
    val relationType: RelationType
    val eventId: EventId
    val replyTo: ReplyTo?

    @Serializable
    data class Reference(
        @SerialName("event_id")
        override val eventId: EventId,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val relationType: RelationType = RelationType.Reference

        @SerialName("m.in_reply_to")
        override val replyTo: ReplyTo? = null
    }

    @Serializable
    data class Replace(
        @SerialName("event_id")
        override val eventId: EventId,
        /**
         * The content used to replace the referenced event.
         * This can be null, because it must not be present in encrypted events.
         */
        @SerialName("m.new_content")
        val newContent: @Contextual MessageEventContent? = null,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val relationType: RelationType = RelationType.Replace

        @SerialName("m.in_reply_to")
        override val replyTo: ReplyTo? = null
    }

    @Serializable
    data class Reply(
        @SerialName("m.in_reply_to")
        override val replyTo: ReplyTo
    ) : RelatesTo {
        @Transient
        override val eventId: EventId = replyTo.eventId

        @Transient
        override val relationType: RelationType = RelationType.Reply
    }

    @Serializable
    data class Thread(
        @SerialName("event_id")
        override val eventId: EventId,
        @SerialName("m.in_reply_to")
        override val replyTo: ReplyTo? = null,
        @SerialName("is_falling_back")
        val isFallingBack: Boolean? = null,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val relationType: RelationType = RelationType.Thread
    }

    data class Unknown(
        val raw: JsonObject,
        override val eventId: EventId,
        override val relationType: RelationType.Unknown,
        override val replyTo: ReplyTo?,
    ) : RelatesTo

    @Serializable
    data class ReplyTo(
        @SerialName("event_id") val eventId: EventId
    )
}

object RelatesToSerializer : KSerializer<RelatesTo> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelatesToSerializer")

    override fun deserialize(decoder: Decoder): RelatesTo {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val replyTo: RelatesTo.ReplyTo? =
            jsonObject[RelationType.Reply.name]?.let { decoder.json.decodeFromJsonElement(it) }
        val relationType: RelationType? =
            jsonObject["rel_type"]?.jsonPrimitive?.let { decoder.json.decodeFromJsonElement(it) }
                ?: replyTo?.let { RelationType.Reply }
        return try {
            when (relationType) {
                is RelationType.Reference -> decoder.json.decodeFromJsonElement<RelatesTo.Reference>(jsonObject)
                is RelationType.Replace -> decoder.json.decodeFromJsonElement<RelatesTo.Replace>(jsonObject)
                is RelationType.Reply -> decoder.json.decodeFromJsonElement<RelatesTo.Reply>(jsonObject)
                is RelationType.Thread -> decoder.json.decodeFromJsonElement<RelatesTo.Thread>(jsonObject)
                else -> {
                    RelatesTo.Unknown(
                        jsonObject,
                        jsonObject["event_id"]?.jsonPrimitive?.content.let { EventId(it ?: "") },
                        relationType?.name.let { RelationType.Unknown(it ?: "") },
                        replyTo,
                    )
                }
            }
        } catch (e: Exception) {
            RelatesTo.Unknown(
                jsonObject,
                jsonObject["event_id"]?.jsonPrimitive?.content.let { EventId(it ?: "") },
                relationType?.name.let { RelationType.Unknown(it ?: "") },
                replyTo,
            )
        }
    }

    override fun serialize(encoder: Encoder, value: RelatesTo) {
        require(encoder is JsonEncoder)
        val jsonObject = when (value) {
            is RelatesTo.Reference -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Replace -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Reply -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Thread -> encoder.json.encodeToJsonElement(value)
            is RelatesTo.Unknown -> JsonObject(buildMap {
                putAll(value.raw)
                put("event_id", JsonPrimitive(value.eventId.full))
                put("rel_type", JsonPrimitive(value.relationType.name))
                value.replyTo?.also { put(RelationType.Reply.name, encoder.json.encodeToJsonElement(it)) }
            })
        }
        encoder.encodeJsonElement(jsonObject)
    }
}