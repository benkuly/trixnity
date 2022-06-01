package net.folivo.trixnity.client.store

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.RoomEventContent

@Serializable
data class TimelineEvent(
    val event: @Contextual RoomEvent<*>,
    /**
     * - event is not encrypted -> original content
     * - event is encrypted
     *     - not yet decrypted -> null
     *     - successfully decrypted -> Result.Success
     *     - failure in decryption -> Result.Failure
     */
    @Transient
    val content: Result<RoomEventContent>? = if (event.isEncrypted) null else Result.success(event.content),

    val roomId: RoomId = event.roomId,
    val eventId: EventId = event.id,

    val previousEventId: EventId?,
    val nextEventId: EventId?,
    val gap: Gap?,
) {
    @Transient
    val isEncrypted: Boolean = event.isEncrypted

    @Transient
    val isFirst: Boolean = previousEventId == null && gap == null

    @Serializable
    data class Gap(
        val batchBefore: String?,
        val batchAfter: String?,
    ) {
        companion object {
            fun before(batchBefore: String) = Gap(batchBefore, null)
            fun after(batchAfter: String) = Gap(null, batchAfter)
            fun both(batchBefore: String, batchAfter: String) = Gap(batchBefore, batchAfter)
        }

        val hasGapBefore: Boolean
            get() = batchBefore != null
        val hasGapAfter: Boolean
            get() = batchAfter != null
        val hasGapBoth: Boolean
            get() = batchBefore != null && batchAfter != null

        fun removeGapBefore() = if (hasGapAfter) copy(batchBefore = null) else null
        fun removeGapAfter() = if (hasGapBefore) copy(batchAfter = null) else null
    }
}
