package net.folivo.trixnity.client.store

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.RoomEventContent

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
    @Transient
    val content: Result<RoomEventContent>? = if (event.isEncrypted) null else Result.success(event.content),

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
