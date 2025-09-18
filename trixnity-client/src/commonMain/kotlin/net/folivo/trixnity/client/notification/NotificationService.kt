package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.MatrixClientStarted
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.toList
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.subscribeAsFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.folivo.trixnity.client.notification.Notification as Notification2


private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.NotificationService")

/**
 * Access and manage user-visible notifications.
 *
 * By default, Trixnity manages its own notifications. It can be seen as some sort of notification center, where notifications can
 * be listed, count and dismissed. Usually this would be kept in sync with the platform notifications. There are
 * platforms, that do not support syncing a list of notifications. For this use case, it is possible to enable
 * [MatrixClientConfiguration.enableExternalNotifications] and get notification updates via [getAllUpdates].
 */
interface NotificationService {
    @Deprecated("use the new notification system instead")
    data class Notification(
        val event: ClientEvent<*>,
        val actions: Set<PushAction>,
    )

    @Deprecated("use the new notification system instead")
    fun getNotifications(
        decryptionTimeout: Duration = 5.seconds,
        syncResponseBufferSize: Int = 4
    ): Flow<Notification>

    @Deprecated("use the new notification system instead")
    fun getNotifications(
        response: Sync.Response,
        decryptionTimeout: Duration = 5.seconds,
    ): Flow<Notification>

    /**
     * Get all notifications.
     */
    fun getAll(): Flow<List<Flow<Notification2?>>>

    /**
     * Notification by id, or null if not available.
     */
    fun getById(id: String): Flow<Notification2?>


    /**
     * Total notification count across all rooms.
     */
    fun getCount(): Flow<Int>

    /**
     * Notification count for the given room.
     */
    fun getCount(roomId: RoomId): Flow<Int>

    /**
     * Mark the notification as dismissed.
     */
    suspend fun dismiss(id: String)

    /**
     * Dismiss all notifications.
     */
    suspend fun dismissAll()

    /**
     * Get all notification updates.
     * This returns an empty flow, when [MatrixClientConfiguration.enableExternalNotifications] is not enabled.
     *
     * This [Flow] should not be buffered. As soon as the next value is requested from a collect operation,
     * the old one will be deleted from the database.
     */
    fun getAllUpdates(): Flow<NotificationUpdate>

    /**
     * Handle a push for a room/event.
     *
     * @return true if no further sync is needed, otherwise false.
     */
    suspend fun onPush(roomId: RoomId, eventId: EventId?): Boolean

    /**
     * Process possibly pending notifications from sync or push if needed.
     * This may suspend for a long time (e.g., when the network is not available but a sync is needed).
     *
     * When [MatrixClientConfiguration.enableExternalNotifications] is enabled, this waits until [getAllUpdates] has collected all updates.
     */
    suspend fun processPending()
}

class NotificationServiceImpl(
    private val roomService: RoomService,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val accountStore: AccountStore,
    private val notificationStore: NotificationStore,
    private val api: MatrixClientServerApiClient,
    private val matrixClientStarted: MatrixClientStarted,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
    private val config: MatrixClientConfiguration,
    private val eventsToNotificationUpdates: EventsToNotificationUpdates,
    coroutineScope: CoroutineScope,
) : NotificationService, EventHandler {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Deprecated("use the new notification system instead")
    override fun getNotifications(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int
    ): Flow<NotificationService.Notification> =
        api.sync.subscribeAsFlow()
            .flatMapConcat { syncEvents -> getNotifications(syncEvents.syncResponse, decryptionTimeout) }

    @Deprecated("use the new notification system instead")
    override fun getNotifications(
        response: Sync.Response,
        decryptionTimeout: Duration
    ): Flow<NotificationService.Notification> = flow {
        val pushRules = globalAccountDataStore.get<PushRulesEventContent>().first()?.content?.global?.toList().orEmpty()
        val room = response.room ?: return@flow
        room.invite?.forEach { (roomId, roomInfo) ->
            eventsToNotificationUpdates.invoke(
                roomId = roomId,
                eventFlow = roomInfo.strippedState?.events.orEmpty().asFlow(),
                pushRules = pushRules,
                existingNotifications = mapOf(),
                removeStale = false
            ).forEach { emit(it.toNotificationUpdate()) }
        }
        room.knock?.forEach { (roomId, roomInfo) ->
            eventsToNotificationUpdates.invoke(
                roomId = roomId,
                eventFlow = roomInfo.strippedState?.events.orEmpty().asFlow(),
                pushRules = pushRules,
                existingNotifications = mapOf(),
                removeStale = false
            ).forEach { emit(it.toNotificationUpdate()) }
        }
        room.join?.forEach { (roomId, roomInfo) ->
            eventsToNotificationUpdates.invoke(
                roomId = roomId,
                eventFlow = (roomInfo.state?.events.orEmpty() + roomInfo.timeline?.events.orEmpty()).asFlow(),
                pushRules = pushRules,
                existingNotifications = mapOf(),
                removeStale = false
            ).forEach { emit(it.toNotificationUpdate()) }
        }
        room.leave?.forEach { (roomId, roomInfo) ->
            eventsToNotificationUpdates.invoke(
                roomId = roomId,
                eventFlow = (roomInfo.state?.events.orEmpty() + roomInfo.timeline?.events.orEmpty()).asFlow(),
                pushRules = pushRules,
                existingNotifications = mapOf(),
                removeStale = false
            ).forEach { emit(it.toNotificationUpdate()) }
        }
    }.toDeprecatedNotifications()

    private fun Flow<NotificationUpdate?>.toDeprecatedNotifications(): Flow<NotificationService.Notification> =
        filterNotNull().mapNotNull { update ->
            when (update) {
                is NotificationUpdate.New ->
                    NotificationService.Notification(
                        event = when (val content = update.content) {
                            is NotificationUpdate.Content.Message -> content.timelineEvent.mergedEvent?.getOrNull()
                                ?: return@mapNotNull null

                            is NotificationUpdate.Content.State -> content.stateEvent
                        },
                        actions = update.actions,
                    )

                is NotificationUpdate.Remove -> null
                is NotificationUpdate.Update -> null
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAll(): Flow<List<Flow<Notification2?>>> =
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

    override fun getById(id: String): Flow<Notification2?> = notificationStore.getById(id).toNotification()

    private val allNotifications =
        notificationStore.getAll().flattenValues()
            .shareIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(stopTimeout = 5.seconds, replayExpiration = 5.seconds),
                replay = 1
            )

    override fun getCount(): Flow<Int> =
        allNotifications.map { notifications -> notifications.size }

    override fun getCount(roomId: RoomId): Flow<Int> =
        allNotifications.map { notifications -> notifications.count { it.roomId == roomId } }

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


    override fun getAllUpdates(): Flow<NotificationUpdate> = flow {
        while (currentCoroutineContext().isActive) {
            val updates =
                notificationStore.getAllUpdates().flattenValues().first { it.isNotEmpty() }.sortedBy { it.sortKey }
            for (storedNotificationUpdate in updates) {
                val notificationUpdate = storedNotificationUpdate.toNotificationUpdate()
                if (notificationUpdate == null) {
                    log.trace { "could not find event for notification update $notificationUpdate" }
                }
                emit(notificationUpdate)
                emit(null) // wait for processed by collector
                notificationStore.updateUpdate(storedNotificationUpdate.id) {
                    if (it == storedNotificationUpdate) null
                    else it
                }
            }
        }
    }.filterNotNull()

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
                is StoredNotificationState.Push,
                is StoredNotificationState.Remove,
                is StoredNotificationState.SyncWithoutTimeline -> it

                is StoredNotificationState.SyncWithTimeline -> it.copy(hasPush = true)
                null -> StoredNotificationState.Push(roomId)
            }
        }
        return false
    }

    private val processPendingMutex = Mutex()
    override suspend fun processPending() {
        processPendingMutex.withLock {
            matrixClientStarted.first { it }
            val hasPending = notificationStore.getAllState().first().values.any { it.first()?.isPending == true }
            if (!hasPending) return

            api.sync.startOnce(
                filter = checkNotNull(accountStore.getAccount()?.backgroundFilterId),
                timeout = Duration.ZERO,
            ).getOrThrow()
            notificationStore.getAllState().flattenValues().first { states -> states.none { it.isPending } }

            if (config.enableExternalNotifications) {
                notificationStore.getAllUpdates().flattenValues().first { it.isEmpty() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<StoredNotification?>.toNotification(): Flow<Notification2?> = flatMapLatest { notification ->
        when (notification) {
            is StoredNotification.Message -> {
                roomService.getTimelineEvent(notification.roomId, notification.eventId)
                    .map { timelineEvent ->
                        if (timelineEvent == null) null
                        else Notification2.Message(
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
                        else Notification2.State(
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun StoredNotificationUpdate.toNotificationUpdate(): NotificationUpdate? {
        return when (this) {
            is StoredNotificationUpdate.New -> {
                NotificationUpdate.New(
                    id = id,
                    sortKey = sortKey,
                    content = content.toNotificationUpdateContent() ?: return null,
                    actions = actions,
                )
            }

            is StoredNotificationUpdate.Update -> {
                NotificationUpdate.Update(
                    id = id,
                    sortKey = sortKey,
                    content = content.toNotificationUpdateContent() ?: return null,
                    actions = actions,
                )
            }

            is StoredNotificationUpdate.Remove -> {
                NotificationUpdate.Remove(
                    id = id,
                    sortKey = sortKey,
                )
            }
        }
    }

    private suspend fun StoredNotificationUpdate.Content.toNotificationUpdateContent(): NotificationUpdate.Content? {
        return when (this) {
            is StoredNotificationUpdate.Content.Message ->
                withTimeoutOrNull(5.seconds) {
                    NotificationUpdate.Content.Message(
                        roomService.getTimelineEvent(roomId, eventId) {
                            decryptionTimeout = 5.seconds
                        }.firstWithContent() ?: return@withTimeoutOrNull null
                    )
                }

            is StoredNotificationUpdate.Content.State ->
                NotificationUpdate.Content.State(
                    roomStateStore.getByStateKey(
                        roomId = roomId,
                        eventContentClass = eventContentSerializerMappings.state.find { it.type == type }?.kClass
                            ?: return null,
                        stateKey = stateKey
                    ).first() ?: return null
                )
        }
    }
}