package net.folivo.trixnity.client.store

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

typealias TimelineEventResult = Result<RoomEventContent>

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
    val content: TimelineEventResult? = if (event.isEncrypted) null else Result.success(event.content),

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
        data object DecryptionTimeout : TimelineEventContentError, RuntimeException()
        data object DecryptionAlgorithmNotSupported : TimelineEventContentError, RuntimeException()
        data class DecryptionError(
            val error: Throwable,
        ) : TimelineEventContentError, RuntimeException(error)

        data object NoContent : TimelineEventContentError, RuntimeException()
    }
}

class TimelineEventResultSerializer(
    private val mappings: EventContentSerializerMappings,
    private val storeTimelineEventContentUnencrypted: Boolean,
) : KSerializer<TimelineEventResult> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TimelineEventResultSerializer")

    private val contentDeserializers: Map<String, KSerializer<out RoomEventContent>> by lazy {
        mappings.state.associate { it.type to it.serializer } +
                mappings.message.associate { it.type to it.serializer }
    }

    override fun deserialize(decoder: Decoder): TimelineEventResult {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val type = checkNotNull(jsonObject["type"]).jsonPrimitive.content
        contentDeserializers[type]
    }
}