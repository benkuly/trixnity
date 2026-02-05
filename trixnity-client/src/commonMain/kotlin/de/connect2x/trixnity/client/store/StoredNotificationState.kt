package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Allows to save the state of notification processing.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface StoredNotificationState {
    val roomId: RoomId
    val needsSync: Boolean
    val needsProcess: Boolean
    val notificationsDisabled: Boolean

    /**
     * There was push notification from external sources.
     */
    @Serializable
    @SerialName("push")
    data class Push(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val needsSync: Boolean = true
        override val needsProcess: Boolean = true
        override val notificationsDisabled = false
    }

    /**
     * The room has been received in a sync with various information including a timeline.
     */
    @Serializable
    @SerialName("sync_with_timeline")
    data class SyncWithTimeline(
        override val roomId: RoomId,
        override val needsSync: Boolean,
        override val notificationsDisabled: Boolean = false, // TODO default value for migration purposes (remove in next major?)
        val readReceipts: Set<EventId>,
        val lastEventId: EventId,
        val lastRelevantEventId: EventId? = null, // TODO default value for migration purposes (remove in next major?)
        val lastProcessedEventId: EventId?,
        val expectedMaxNotificationCount: Long?,
        val isRead: IsRead = IsRead.CHECK, // TODO default value for migration purposes (remove in next major?)
    ) : StoredNotificationState {
        override val needsProcess: Boolean get() = lastEventId != lastProcessedEventId || isRead.needsCheck

        @Serializable
        enum class IsRead {
            TRUE, FALSE, TRUE_BUT_CHECK, FALSE_BUT_CHECK, CHECK;

            val needsCheck: Boolean get() = this == TRUE_BUT_CHECK || this == FALSE_BUT_CHECK || this == CHECK
        }
    }

    /**
     * The room has been received in a sync without a timeline.
     */
    @Serializable
    @SerialName("sync_without_timeline")
    data class SyncWithoutTimeline(
        override val roomId: RoomId,
        override val notificationsDisabled: Boolean = false, // TODO default value for migration purposes (remove in next major?)
    ) : StoredNotificationState {
        override val needsSync: Boolean = false
        override val needsProcess: Boolean = true
    }

    /**
     * Notifications and this state are scheduled to be removed.
     */
    @Serializable
    @SerialName("read")
    data class Read(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val needsSync: Boolean = false
        override val needsProcess: Boolean = true
        override val notificationsDisabled = true
    }
}
