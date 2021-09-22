package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event.MegolmEvent
import net.folivo.trixnity.core.model.events.Event.RoomEvent

data class TimelineEvent(
    val event: RoomEvent<*>,
    val decryptedEvent: Result<MegolmEvent<*>>? = null,

    val roomId: RoomId,
    val eventId: EventId,

    val previousEventId: EventId?,
    val nextEventId: EventId?,
    val gap: Gap?,
) {
    sealed interface Gap {
        val batch: String

        data class GapBefore(
            override val batch: String,
        ) : Gap

        data class GapBoth(
            override val batch: String,
        ) : Gap

        data class GapAfter(
            override val batch: String,
        ) : Gap
    }
}
