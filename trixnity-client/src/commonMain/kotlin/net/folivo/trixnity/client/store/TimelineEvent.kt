package net.folivo.trixnity.client.store

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.RoomEventContent

@Serializable
data class TimelineEvent(
    val event: @Contextual RoomEvent<*>,
    /**
     * If the event is encrypted, this will be the decrypted event content. If the event is not encrypted, this will immediately
     * contain the event content from the event. Therefore, if this is null, the event content is not yet decrypted.
     */
    @Transient
    val content: Result<RoomEventContent>? = if (event.isEncrypted) null else Result.success(event.content),

    val roomId: RoomId,
    val eventId: EventId,

    val previousEventId: EventId?,
    val nextEventId: EventId?,
    val gap: Gap?,
) {
    @Transient
    val isEncrypted: Boolean = event.isEncrypted

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonClassDiscriminator("position")
    sealed class Gap {
        abstract val batch: String

        @Serializable
        @SerialName("before")
        data class GapBefore(
            override val batch: String,
        ) : Gap()

        @Serializable
        @SerialName("both")
        data class GapBoth(
            override val batch: String,
        ) : Gap()

        @Serializable
        @SerialName("after")
        data class GapAfter(
            override val batch: String,
        ) : Gap()
    }
}
