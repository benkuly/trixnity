package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.mergeContentOrNull
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMapping
import de.connect2x.trixnity.core.serialization.events.RedactedEventContentSerializer
import de.connect2x.trixnity.core.serialization.events.UnknownEventContentSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TimelineEvent(
    val event: RoomEvent<*>,
    /**
     * - event is not encrypted -> original content
     * - event is encrypted
     *     - not yet decrypted -> null
     *     - successfully decrypted -> Result.Success
     *     - failure in decryption -> Result.Failure (contains TimelineEventContentError)
     *
     *  The content may be replaced by another events content.
     */
    val content: Result<RoomEventContent>? = if (event.isEncrypted) null else Result.success(event.content),

    val previousEventId: EventId? = null,
    val nextEventId: EventId? = null,
    val gap: Gap? = null,
) {
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonClassDiscriminator("position")
    sealed interface Gap {
        val batchBefore: String?
        val batchAfter: String?

        @Serializable
        @SerialName("before")
        data class GapBefore(
            override val batchBefore: String,
        ) : Gap {
            @Transient
            override val batchAfter: String? = null
        }

        @Serializable
        @SerialName("both")
        data class GapBoth(
            override val batchBefore: String,
            override val batchAfter: String,
        ) : Gap

        @Serializable
        @SerialName("after")
        data class GapAfter(
            override val batchAfter: String,
        ) : Gap {
            @Transient
            override val batchBefore: String? = null
        }

        val hasGapBefore: Boolean
            get() = batchBefore != null
        val hasGapAfter: Boolean
            get() = batchAfter != null
        val hasGapBoth: Boolean
            get() = batchBefore != null && batchAfter != null

        fun removeGapBefore() = batchAfter?.let { GapAfter(it) }
        fun removeGapAfter() = batchBefore?.let { GapBefore(it) }
    }

    sealed interface TimelineEventContentError {
        data object DecryptionTimeout : TimelineEventContentError,
            RuntimeException("timeout while decrypting TimelineEvent")

        data object DecryptionAlgorithmNotSupported : TimelineEventContentError,
            RuntimeException("algorithm not supported for decrypting event")

        data class DecryptionError(
            val error: Throwable,
        ) : TimelineEventContentError, RuntimeException("error while decrypting TimelineEvent", error)

        data object NoContent : TimelineEventContentError, RuntimeException("no content found to replace TimelineEvent")
    }

    /**
     * This merges [event] and [content] into one property.
     */
    val mergedEvent: Result<RoomEvent<*>>? by lazy {
        content?.fold(
            onSuccess = { finalContent ->
                event.mergeContentOrNull(finalContent)?.let { Result.success(it) }
            },
            onFailure = { Result.failure(it) }
        )
    }

    class Serializer(
        private val mappings: Set<EventContentSerializerMapping<out RoomEventContent>>,
        private val storeTimelineEventContentUnencrypted: Boolean,
    ) : KSerializer<TimelineEvent> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TimelineEvent")

        private val contentDeserializers: Map<String, KSerializer<out RoomEventContent>> by lazy {
            mappings.associate { it.type to it.serializer }
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun deserialize(decoder: Decoder): TimelineEvent {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val event: RoomEvent<*> = checkNotNull(jsonObject["event"]).jsonObject.let {
                val serializer = decoder.json.serializersModule.getContextual(RoomEvent::class)
                checkNotNull(serializer)
                decoder.json.decodeFromJsonElement(serializer, it)
            }
            val content: Result<RoomEventContent>? = (jsonObject["content"] as? JsonObject)?.let {
                val type = checkNotNull(it["type"]).jsonPrimitive.content
                val serializer = contentDeserializers[type] ?: UnknownEventContentSerializer(type)
                val value = checkNotNull(it["value"])
                Result.success(decoder.json.decodeFromJsonElement(serializer, value))
            }
            val previousEventId: EventId? = jsonObject["previousEventId"]?.let {
                decoder.json.decodeFromJsonElement(it)
            }
            val nextEventId: EventId? = jsonObject["nextEventId"]?.let {
                decoder.json.decodeFromJsonElement(it)
            }
            val gap: TimelineEvent.Gap? = jsonObject["gap"]?.let {
                decoder.json.decodeFromJsonElement(it)
            }
            return if (content != null) {
                TimelineEvent(
                    event = event,
                    content = content,
                    previousEventId = previousEventId,
                    nextEventId = nextEventId,
                    gap = gap
                )
            } else {
                TimelineEvent(
                    event = event,
                    previousEventId = previousEventId,
                    nextEventId = nextEventId,
                    gap = gap
                )
            }
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun serialize(encoder: Encoder, value: TimelineEvent) {
            require(encoder is JsonEncoder)
            val event: JsonElement = value.event.let {
                val serializer = encoder.json.serializersModule.getContextual(RoomEvent::class)
                checkNotNull(serializer)
                encoder.json.encodeToJsonElement(serializer, it)
            }
            val content: JsonObject? = value.content?.getOrNull()?.let { content ->
                if (!storeTimelineEventContentUnencrypted || !value.isEncrypted) {
                    null
                } else {
                    var type: String? = null
                    var serializer: KSerializer<out RoomEventContent>? = null
                    val mapping = mappings.firstOrNull { it.kClass.isInstance(content) }
                    when {
                        mapping != null -> {
                            type = mapping.type
                            serializer = mapping.serializer
                        }

                        content is RedactedEventContent -> {
                            type = content.eventType
                            serializer = RedactedEventContentSerializer(type)
                        }

                        content is UnknownEventContent -> {
                            type = content.eventType
                            serializer = UnknownEventContentSerializer(type)
                        }
                    }
                    if (type == null || serializer == null) {
                        null
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val jsonValue =
                            encoder.json.encodeToJsonElement(serializer as KSerializer<RoomEventContent>, content)
                        JsonObject(buildMap {
                            put("type", JsonPrimitive(type))
                            put("value", jsonValue)
                        })
                    }
                }
            }
            val previousEventId: JsonElement? = value.previousEventId?.let {
                encoder.json.encodeToJsonElement(it)
            }
            val nextEventId: JsonElement? = value.nextEventId?.let {
                encoder.json.encodeToJsonElement(it)
            }
            val gap: JsonElement? = value.gap?.let {
                encoder.json.encodeToJsonElement(it)
            }
            encoder.encodeJsonElement(JsonObject(buildMap {
                put("event", event)
                if (content != null) put("content", content)
                if (previousEventId != null) put("previousEventId", previousEventId)
                if (nextEventId != null) put("nextEventId", nextEventId)
                if (gap != null) put("gap", gap)
            }))
        }
    }
}
