package net.folivo.trixnity.client.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface StoredNotificationState {
    val roomId: RoomId
    val hasPush: Boolean

    @Serializable
    @SerialName("push")
    data class Push(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val hasPush: Boolean = true
    }

    @Serializable
    @SerialName("sync_with_timeline")
    data class SyncWithTimeline(
        override val roomId: RoomId,
        override val hasPush: Boolean,
        val readReceipts: Set<EventId>,
        val lastEventId: EventId,
        val lastProcessedEventId: EventId?,
        val expectedMaxNotificationCount: Long?,
    ) : StoredNotificationState

    @Serializable
    @SerialName("sync_without_timeline")
    data class SyncWithoutTimeline(
        override val roomId: RoomId,
        override val hasPush: Boolean,
    ) : StoredNotificationState

    @Serializable
    @SerialName("remove")
    data class Remove(
        override val roomId: RoomId,
    ) : StoredNotificationState {
        override val hasPush: Boolean = false
    }
}
