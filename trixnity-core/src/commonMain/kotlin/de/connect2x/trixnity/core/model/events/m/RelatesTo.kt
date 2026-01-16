package de.connect2x.trixnity.core.model.events.m

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.MessageEventContent

private val log = KotlinLogging.logger("de.connect2x.trixnity.core.model.events.m.RelatesTo")

@Serializable(with = RelatesTo.Serializer::class)
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

    @Serializable
    data class Annotation(
        @SerialName("event_id")
        override val eventId: EventId,
        @SerialName("key")
        val key: String? = null,
    ) : RelatesTo {
        @SerialName("rel_type")
        override val relationType: RelationType = RelationType.Annotation

        @Transient
        override val replyTo: ReplyTo? = null
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

    object Serializer : KSerializer<RelatesTo> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelatesTo")

        override fun deserialize(decoder: Decoder): RelatesTo {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val replyTo: ReplyTo? =
                try {
                    jsonObject[RelationType.Reply.name]?.let { decoder.json.decodeFromJsonElement(it) }
                } catch (e: Exception) {
                    log.warn(e) { "malformed reply" }
                    null
                }
            val relationType: RelationType? =
                try {
                    (jsonObject["rel_type"] as? JsonPrimitive)?.let { decoder.json.decodeFromJsonElement(it) }
                        ?: replyTo?.let { RelationType.Reply }
                } catch (e: Exception) {
                    log.warn(e) { "malformed rel_type" }
                    null
                }
            return try {
                when (relationType) {
                    is RelationType.Reference -> decoder.json.decodeFromJsonElement<Reference>(jsonObject)
                    is RelationType.Replace -> decoder.json.decodeFromJsonElement<Replace>(jsonObject)
                    is RelationType.Reply -> decoder.json.decodeFromJsonElement<Reply>(jsonObject)
                    is RelationType.Thread -> decoder.json.decodeFromJsonElement<Thread>(jsonObject)
                    is RelationType.Annotation -> decoder.json.decodeFromJsonElement<Annotation>(jsonObject)
                    else -> {
                        Unknown(
                            jsonObject,
                            EventId((jsonObject["event_id"] as? JsonPrimitive)?.contentOrNull ?: ""),
                            relationType?.name.let { RelationType.Unknown(it ?: "") },
                            replyTo,
                        )
                    }
                }
            } catch (_: Exception) {
                Unknown(
                    jsonObject,
                    EventId((jsonObject["event_id"] as? JsonPrimitive)?.contentOrNull ?: ""),
                    relationType?.name.let { RelationType.Unknown(it ?: "") },
                    replyTo,
                )
            }
        }

        override fun serialize(encoder: Encoder, value: RelatesTo) {
            require(encoder is JsonEncoder)
            val jsonObject = when (value) {
                is Reference -> encoder.json.encodeToJsonElement(value)
                is Replace -> encoder.json.encodeToJsonElement(value)
                is Reply -> encoder.json.encodeToJsonElement(value)
                is Thread -> encoder.json.encodeToJsonElement(value)
                is Annotation -> encoder.json.encodeToJsonElement(value)
                is Unknown -> JsonObject(buildMap {
                    putAll(value.raw)
                    put("event_id", JsonPrimitive(value.eventId.full))
                    put("rel_type", JsonPrimitive(value.relationType.name))
                    value.replyTo?.also { put(RelationType.Reply.name, encoder.json.encodeToJsonElement(it)) }
                })
            }
            encoder.encodeJsonElement(jsonObject)
        }
    }
}