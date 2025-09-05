package net.folivo.trixnity.client.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

/**
 * Allows to save the state of notification processing.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface StoredNotificationState {
    val roomId: RoomId
    val isPending: Boolean

    /**
     * There was push notification from external sources.
     */
    @Serializable
    @SerialName("push")
    data class Push(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val isPending: Boolean = true
    }

    /**
     * The room has been received in a sync with various information including a timeline.
     */
    @Serializable
    @SerialName("sync_with_timeline")
    data class SyncWithTimeline(
        override val roomId: RoomId,
        val hasPush: Boolean,
        val readReceipts: Set<EventId>,
        val lastEventId: EventId,
        val lastProcessedEventId: EventId?,
        val expectedMaxNotificationCount: Long?,
    ) : StoredNotificationState {
        override val isPending: Boolean = lastEventId != lastProcessedEventId || hasPush
    }

    /**
     * The room has been received in a sync without a timeline.
     */
    @Serializable
    @SerialName("sync_without_timeline")
    data class SyncWithoutTimeline(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val isPending: Boolean = true
    }

    /**
     * Notifications and this state are scheduled to be removed.
     */
    @Serializable
    @SerialName("remove")
    data class Remove(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val isPending: Boolean = true
    }
}
