package net.folivo.trixnity.client.store

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMapping
import net.folivo.trixnity.core.serialization.events.RedactedEventContentSerializer
import net.folivo.trixnity.core.serialization.events.UnknownEventContentSerializer

typealias TimelineEventContentResult = Result<RoomEventContent>

@Serializable
data class TimelineEvent(
    val event: @Contextual ClientEvent.RoomEvent<*>,
    /**
     * - event is not encrypted -> original content
     * - event is encrypted
     *     - not yet decrypted -> null
     *     - successfully decrypted -> Result.Success
     *     - failure in decryption -> Result.Failure (contains TimelineEventContentError)
     *
     *  The content may be replaced by another event.
     */
    @Contextual
    val content: TimelineEventContentResult? = if (event.isEncrypted) null else Result.success(event.content),

    val roomId: RoomId = event.roomId,
    val eventId: EventId = event.id,

    val previousEventId: EventId?,
    val nextEventId: EventId?,
    val gap: Gap?,
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
}


// FIXME put into json
class TimelineEventContentResultSerializer(
    private val mappings: Set<EventContentSerializerMapping<out RoomEventContent>>,
    private val storeTimelineEventContentUnencrypted: Boolean,
) : KSerializer<TimelineEventContentResult> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TimelineEventResultSerializer")

    private val contentDeserializers: Map<String, KSerializer<out RoomEventContent>> by lazy {
        mappings.associate { it.type to it.serializer }
    }

    override fun deserialize(decoder: Decoder): TimelineEventContentResult {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val type = checkNotNull(jsonObject["type"]).jsonPrimitive.content
        val serializer = contentDeserializers[type] ?: UnknownEventContentSerializer(type)
        val content = checkNotNull(jsonObject["value"])
        return Result.success(decoder.json.decodeFromJsonElement(serializer, content))
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: TimelineEventContentResult) {
        require(encoder is JsonEncoder)
        val content = value.getOrNull()
        if (!storeTimelineEventContentUnencrypted || content == null) {
            encoder.encodeNull()
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
                encoder.encodeNull()
            } else {
                @Suppress("UNCHECKED_CAST")
                val jsonValue = encoder.json.encodeToJsonElement(serializer as KSerializer<RoomEventContent>, content)
                encoder.encodeJsonElement(
                    JsonObject(buildMap {
                        put("type", JsonPrimitive(type))
                        put("value", jsonValue)
                    })
                )
            }
        }
    }
}