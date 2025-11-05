package net.folivo.trixnity.client.store

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.RedactedEventContentSerializer
import net.folivo.trixnity.core.serialization.events.UnknownEventContentSerializer

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
                val originalEvent = event
                when {
                    originalEvent is RoomEvent.MessageEvent<*> && finalContent is MessageEventContent ->
                        Result.success(
                            RoomEvent.MessageEvent(
                                content = finalContent,
                                id = originalEvent.id,
                                sender = originalEvent.sender,
                                roomId = originalEvent.roomId,
                                originTimestamp = originalEvent.originTimestamp,
                                unsigned = originalEvent.unsigned,
                            )
                        )

                    originalEvent is RoomEvent.StateEvent<*> && finalContent is StateEventContent ->
                        Result.success(
                            RoomEvent.StateEvent(
                                content = finalContent,
                                id = originalEvent.id,
                                sender = originalEvent.sender,
                                roomId = originalEvent.roomId,
                                originTimestamp = originalEvent.originTimestamp,
                                unsigned = originalEvent.unsigned,
                                stateKey = originalEvent.stateKey,
                            )
                        )

                    else -> null
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
}


class TimelineEventSerializer(
    private val mappings: Set<EventContentSerializerMapping<out RoomEventContent>>,
    private val storeTimelineEventContentUnencrypted: Boolean,
) : KSerializer<TimelineEvent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TimelineEventSerializer")

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