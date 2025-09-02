package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClientStarted
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.NotificationService")

/**
 * A notification can be of type [Message] or [State].
 */
sealed interface Notification {
    /**
     * Unique identifier, that can be used for various operations in [NotificationService].
     */
    val id: String

    /**
     * Can be used to sort the notification.
     */
    val sortKey: String

    /**
     * The [PushAction] that should be performed, when the notification is created on the device.
     */
    val actions: Set<PushAction>

    /**
     * Indicates, that a user has dismissed a notification on the device.
     */
    val dismissed: Boolean

    data class Message(
        override val id: String,
        override val sortKey: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean,
        val timelineEvent: TimelineEvent,
    ) : Notification

    data class State(
        override val id: String,
        override val sortKey: String,
        override val actions: Set<PushAction>,
        override val dismissed: Boolean,
        val stateEvent: ClientEvent.StateBaseEvent<*>,
    ) : Notification
}

/**
 * Access and manage user-visible notifications.
 */
interface NotificationService {

    /**
     * Get all notifications.
     */
    fun getAll(): Flow<List<Flow<Notification?>>>

    /**
     * Notification by id, or null if not available.
     */
    fun getById(id: String): Flow<Notification?>

    /**
     * Total notification count across all rooms.
     */
    fun getNotificationCount(): Flow<Int>

    /**
     * Notification count for the given room.
     */
    fun getNotificationCount(roomId: RoomId): Flow<Int>

    /**
     * Mark the notification as dismissed.
     */
    suspend fun dismiss(id: String)

    /**
     * Dismiss all notifications.
     */
    suspend fun dismissAll()

    /**
     * Handle a push for a room/event.
     *
     * @return true if no further sync is needed, otherwise false.
     */
    suspend fun onPush(roomId: RoomId, eventId: EventId?): Boolean

    /**
     * Process possibly pending push notifications if needed.
     * This may suspend for a long time (e.g., when the network is not available)
     */
    suspend fun processPush()
}

class NotificationServiceImpl(
    private val roomService: RoomService,
    private val roomStateStore: RoomStateStore,
    private val accountStore: AccountStore,
    private val notificationStore: NotificationStore,
    private val api: MatrixClientServerApiClient,
    private val matrixClientStarted: MatrixClientStarted,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
    coroutineScope: CoroutineScope,
) : NotificationService, EventHandler {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAll(): Flow<List<Flow<Notification?>>> =
        notificationStore.getAll().flatMapLatest { notifications ->
            if (notifications.isEmpty()) flowOf(emptyList())
            else {
                val innerFlowsWithSortKey = notifications.values.map { entry -> entry.map { entry to it?.sortKey } }
                combine(innerFlowsWithSortKey) { innerFlowsWithCreatedAtArray ->
                    innerFlowsWithCreatedAtArray
                        .mapNotNull { entry -> entry.second?.let { entry.first to entry.second } }
                        .sortedBy { it.second }
                        .map { it.first.toNotification() }
                }
            }
        }.distinctUntilChanged()

    override fun getById(id: String): Flow<Notification?> = notificationStore.getById(id).toNotification()

    private val processedNotifications =
        notificationStore.getAll().flattenValues()
            .shareIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(stopTimeout = 5.seconds, replayExpiration = 5.seconds),
                replay = 1
            )

    override fun getNotificationCount(): Flow<Int> =
        processedNotifications.map { notifications -> notifications.size }

    override fun getNotificationCount(roomId: RoomId): Flow<Int> =
        processedNotifications.map { notifications -> notifications.count { it.roomId == roomId } }

    override suspend fun dismiss(id: String) {
        notificationStore.update(id) { notification ->
            when (notification) {
                is StoredNotification.Message -> notification.copy(dismissed = true)
                is StoredNotification.State -> notification.copy(dismissed = true)
                null -> null
            }
        }
    }

    override suspend fun dismissAll() = notificationStore.getAll().flatten().first().forEach { dismiss(it.key) }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<StoredNotification?>.toNotification(): Flow<Notification?> = flatMapLatest { notification ->
        when (notification) {
            is StoredNotification.Message -> {
                roomService.getTimelineEvent(notification.roomId, notification.eventId)
                    .map { timelineEvent ->
                        if (timelineEvent == null) null
                        else Notification.Message(
                            id = notification.id,
                            sortKey = notification.sortKey,
                            actions = notification.actions,
                            dismissed = notification.dismissed,
                            timelineEvent = timelineEvent,
                        )
                    }
            }

            is StoredNotification.State -> {
                val eventContentClass =
                    eventContentSerializerMappings.state.find { it.type == notification.type }?.kClass
                if (eventContentClass == null) {
                    log.warn { "could not resolve type for notification ${notification.id}" }
                    flowOf(null)
                } else {
                    roomStateStore.getByStateKey(
                        roomId = notification.roomId,
                        eventContentClass = eventContentClass,
                        stateKey = notification.stateKey
                    ).map { stateEvent ->
                        if (stateEvent == null) null
                        else Notification.State(
                            id = notification.id,
                            sortKey = notification.sortKey,
                            actions = notification.actions,
                            dismissed = notification.dismissed,
                            stateEvent = stateEvent,
                        )
                    }
                }
            }

            null -> flowOf(null)
        }
    }

    override suspend fun onPush(roomId: RoomId, eventId: EventId?): Boolean {
        if (eventId != null) {
            val foundNotificationInStore =
                notificationStore.getAll().first().map { it.value.first() }.any {
                    it is StoredNotification.Message && it.roomId == roomId && it.eventId == eventId ||
                            it is StoredNotification.State && it.roomId == roomId && it.eventId == eventId
                }
            if (foundNotificationInStore) return true

            val foundInTimeline = roomService.getTimelineEvent(roomId, eventId).first() != null
            if (foundInTimeline) return true
        }

        notificationStore.updateState(roomId) {
            when (it) {
                is StoredNotificationState.Push -> it
                is StoredNotificationState.Remove -> it
                is StoredNotificationState.SyncWithTimeline -> it.copy(hasPush = true)
                is StoredNotificationState.SyncWithoutTimeline -> it.copy(hasPush = true)
                null -> StoredNotificationState.Push(roomId)
            }
        }
        return false
    }

    override suspend fun processPush() {
        matrixClientStarted.first { it }
        val hasPush = notificationStore.getAllState().first().any { it.value.first()?.hasPush == true }
        if (!hasPush) return
        api.sync.startOnce(
            filter = checkNotNull(accountStore.getAccount()?.backgroundFilterId),
            timeout = Duration.ZERO,
        ).getOrThrow()
        notificationStore.getAllState().flatten().first { it.any { it.value?.hasPush == false } }
    }
}