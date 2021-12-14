package net.folivo.trixnity.client.store

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.MegolmEvent
import net.folivo.trixnity.core.model.events.Event.RoomEvent

@Serializable
data class TimelineEvent(
    val event: @Contextual RoomEvent<*>,
    @Transient
    val decryptedEvent: Result<MegolmEvent<*>>? = null,

    val roomId: RoomId,
    val eventId: EventId,

    val previousEventId: EventId?,
    val nextEventId: EventId?,
    val gap: Gap?,
) {
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
