package de.connect2x.trixnity.client.notification

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.key.getDeviceKey
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.room.firstWithContent
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.client.store.StoredNotificationState.SyncWithTimeline.IsRead
import de.connect2x.trixnity.client.takeWhileInclusive
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.PushRulesEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.push.toList
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = Logger("de.connect2x.trixnity.client.notification.NotificationEventHandler")

class NotificationEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomService: RoomService,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomUserStore: RoomUserStore,
    private val keyStore: KeyStore,
    globalAccountDataStore: GlobalAccountDataStore,
    private val notificationStore: NotificationStore,
    private val eventsToNotificationUpdates: EventsToNotificationUpdates,
    private val currentSyncState: CurrentSyncState,
    private val transactionManager: TransactionManager,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
    private val config: MatrixClientConfiguration,
    coroutineScope: CoroutineScope,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(subscriber = ::processSync).unsubscribeOnCompletion(scope)
        scope.launch { processNotificationStates() }
    }

    internal class PushRulesCache(
        val content: PushRulesEventContent?
    ) {
        val pushRules = content?.global?.toList().orEmpty()
        val pushRulesDisabled by lazy { isPushRulesDisabled(pushRules) }
        val pushRulesDisabledByRoom by lazy { getRoomsWithDisabledPushRules(pushRules) }
    }

    private val pushRulesCache = globalAccountDataStore.get<PushRulesEventContent>()
        .map { it?.content }
        .distinctUntilChanged()
        .map { PushRulesCache(it) }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    internal suspend fun processSync(syncEvents: SyncEvents) {
        val hasNewPushRules =
            syncEvents.syncResponse.accountData?.events?.any { it.content is PushRulesEventContent } == true
        val allState = notificationStore.getAllState().first().values.mapNotNull { it.first() }

        val pushRulesCache = pushRulesCache.first()
        val disabledRooms =
            (if (hasNewPushRules && pushRulesCache.pushRulesDisabled)
                notificationStore.getAll().first()
                    .values.mapNotNull { it.first() }
                    .map { it.roomId }
                    .toSet()
            else emptySet()) + pushRulesCache.pushRulesDisabledByRoom

        val allUpdatedRooms = syncEvents.syncResponse.room?.run {
            join?.filterValues {
                it.timeline?.events.isNullOrEmpty().not()
                        || it.state?.events.isNullOrEmpty().not()
                        || it.stateAfter?.events.isNullOrEmpty().not()
                        || it.ephemeral?.events?.any {
                    val content = it.content
                    content is ReceiptEventContent && content.events.any {
                        it.value.values.any { it.keys.contains(userInfo.userId) }
                    }
                } == true
            }?.keys.orEmpty() +
                    invite?.filterValues { it.strippedState?.events.isNullOrEmpty().not() }?.keys.orEmpty() +
                    knock?.filterValues { it.strippedState?.events.isNullOrEmpty().not() }?.keys.orEmpty() +
                    leave?.filterValues {
                        it.timeline?.events.isNullOrEmpty().not()
                                || it.state?.events.isNullOrEmpty().not()
                                || it.stateAfter?.events.isNullOrEmpty().not()
                    }?.keys.orEmpty()
        }.orEmpty()
            .toSet()

        data class RoomWithReadMarker(
            val roomId: RoomId,
            val readReceipts: Set<EventId>,
            val lastEventId: EventId?,
            val lastRelevantEventId: EventId?,
            val isEncrypted: Boolean,
            val notificationsDisabled: Boolean,
        )

        val (completelyReadRooms, unreadRooms) =
            allUpdatedRooms.mapNotNull { roomId ->
                val room = roomStore.get(roomId).first()
                if (room != null) {
                    val ownReceipts = roomUserStore.getReceipts(userInfo.userId, roomId).first()?.receipts
                    RoomWithReadMarker(
                        roomId = roomId,
                        readReceipts = setOfNotNull(
                            ownReceipts?.get(ReceiptType.Read)?.eventId,
                            ownReceipts?.get(ReceiptType.PrivateRead)?.eventId
                        ),
                        lastEventId = room.lastEventId,
                        lastRelevantEventId = room.lastRelevantEventId,
                        isEncrypted = room.encrypted,
                        notificationsDisabled = pushRulesCache.pushRulesDisabled || disabledRooms.contains(roomId)
                    )
                } else null
            }.partition {
                it.lastEventId != null && it.readReceipts.contains(it.lastEventId)
            }.let { roomWithReadMarker ->
                roomWithReadMarker.first.map { it.roomId }.toSet() to roomWithReadMarker.second.toSet()
            }

        val oldPushStates =
            allState.filterIsInstance<StoredNotificationState.Push>()
                .map { it.roomId }.toSet() - completelyReadRooms.toSet() - unreadRooms.map { it.roomId }.toSet()

        val removeNotificationRooms =
            if (hasNewPushRules)
                disabledRooms - completelyReadRooms - unreadRooms.map { it.roomId }.toSet() - oldPushStates
            else emptySet()

        if (completelyReadRooms.isEmpty() && unreadRooms.isEmpty() && oldPushStates.isEmpty() && removeNotificationRooms.isEmpty()) {
            log.trace { "skip because no changes" }
            return
        }

        if (removeNotificationRooms.isNotEmpty())
            log.debug { "schedule remove all notifications for push rule disabled rooms $removeNotificationRooms" }
        if (completelyReadRooms.isNotEmpty())
            log.debug { "schedule remove all notifications and state for completely read rooms $completelyReadRooms" }
        if (unreadRooms.isNotEmpty())
            log.debug { "schedule notification processing for unread rooms $unreadRooms" }
        if (oldPushStates.isNotEmpty())
            log.debug { "remove old push state: $oldPushStates" }

        transactionManager.writeTransaction {
            completelyReadRooms.forEach { roomId ->
                notificationStore.updateState(roomId) {
                    StoredNotificationState.Read(roomId)
                }
            }
            removeNotificationRooms.forEach { roomId ->
                notificationStore.updateState(roomId) { oldState ->
                    when (oldState) {
                        is StoredNotificationState.SyncWithoutTimeline -> oldState.copy(notificationsDisabled = true)
                        is StoredNotificationState.SyncWithTimeline -> oldState.copy(notificationsDisabled = true)
                        is StoredNotificationState.Push,
                        is StoredNotificationState.Read -> oldState

                        null -> StoredNotificationState.Read(roomId)
                    }
                }
            }
            unreadRooms.forEach { unreadRoom ->
                val roomId = unreadRoom.roomId
                val lastEventId = unreadRoom.lastEventId
                notificationStore.updateState(unreadRoom.roomId) { oldState ->
                    if (lastEventId != null) {
                        val oldTimelineState = oldState as? StoredNotificationState.SyncWithTimeline
                        val lastRelevantEventIdChanged =
                            oldTimelineState?.lastRelevantEventId != unreadRoom.lastRelevantEventId
                        val receiptsChanged = oldTimelineState?.readReceipts != unreadRoom.readReceipts
                        val notificationsDisabledChanged =
                            oldTimelineState?.notificationsDisabled != unreadRoom.notificationsDisabled
                        StoredNotificationState.SyncWithTimeline(
                            roomId = roomId,
                            needsSync = false,
                            readReceipts = unreadRoom.readReceipts,
                            lastEventId = lastEventId,
                            lastRelevantEventId = unreadRoom.lastRelevantEventId,
                            lastProcessedEventId = when {
                                oldTimelineState == null && unreadRoom.readReceipts.isEmpty() -> lastEventId
                                receiptsChanged || notificationsDisabledChanged -> null
                                else -> oldState.lastProcessedEventId
                            },
                            expectedMaxNotificationCount = when {
                                unreadRoom.isEncrypted.not() ->
                                    syncEvents.syncResponse.room?.join?.get(roomId)?.unreadNotifications?.notificationCount
                                        ?: oldTimelineState?.expectedMaxNotificationCount

                                else -> null
                            },
                            isRead = when {
                                unreadRoom.readReceipts.contains(unreadRoom.lastRelevantEventId) -> IsRead.TRUE
                                oldTimelineState?.isRead == IsRead.TRUE && lastRelevantEventIdChanged.not() -> IsRead.TRUE
                                oldTimelineState?.isRead == IsRead.FALSE && receiptsChanged.not() -> IsRead.FALSE
                                oldTimelineState?.isRead == IsRead.TRUE -> IsRead.TRUE_BUT_CHECK
                                oldTimelineState?.isRead == IsRead.FALSE -> IsRead.FALSE_BUT_CHECK
                                else -> oldTimelineState?.isRead ?: IsRead.CHECK
                            },
                            notificationsDisabled = unreadRoom.notificationsDisabled,
                        )
                    } else {
                        StoredNotificationState.SyncWithoutTimeline(
                            roomId = roomId,
                            notificationsDisabled = unreadRoom.notificationsDisabled,
                        )
                    }
                }
            }
            oldPushStates.forEach { oldPushState ->
                notificationStore.updateState(oldPushState) { null }
            }
        }
    }

    private suspend fun processNotificationStates() {
        if (currentSyncState.value == SyncState.INITIAL_SYNC) {
            log.debug { "waiting for sync state to be running before calculating notifications" }
            currentSyncState.first { it != SyncState.INITIAL_SYNC }
        }
        if (keyStore.getDeviceKey(userInfo.userId, userInfo.deviceId).first()?.trustLevel?.isVerified == true) {
            log.debug { "waiting for device to be cross signed before calculating notifications" }
            keyStore.getDeviceKey(userInfo.userId, userInfo.deviceId)
                .first { it?.trustLevel?.isVerified == true }
        }
        log.info { "starting notification calculations" }
        notificationStore.getAllState().flattenValues().collect { notificationStates ->
            val pushRulesCache = pushRulesCache.first()
            coroutineScope {
                notificationStates
                    .filter { it.needsProcess }
                    .forEach { notificationState ->
                        launch {
                            processNotificationState(notificationState, pushRulesCache)
                        }
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun processNotificationState(
        notificationState: StoredNotificationState,
        pushRulesCache: PushRulesCache,
    ) {
        val roomId = notificationState.roomId

        when (notificationState) {
            is StoredNotificationState.Push -> {
                log.debug { "skip notification processing with $notificationState" }
            }

            is StoredNotificationState.Read -> {
                log.debug { "notification removing with $notificationState" }
                if (config.enableExternalNotifications) {
                    val notificationUpdates =
                        getAllNotifications(roomId)
                            .map { StoredNotificationUpdate.Remove(it.key, roomId) }
                    notificationStore.saveAllUpdates(notificationUpdates)
                }
                notificationStore.deleteNotificationsByRoomId(roomId)
                notificationStore.updateState(roomId) {
                    if (it is StoredNotificationState.Read) null
                    else it
                }
            }

            is StoredNotificationState.SyncWithoutTimeline -> {
                if (notificationState.notificationsDisabled) {
                    log.debug { "notification removing with $notificationState" }
                    notificationStore.deleteNotificationsByRoomId(roomId)
                } else {
                    log.debug { "notification processing with $notificationState" }
                    val notificationUpdates =
                        eventsToNotificationUpdates(
                            roomId = roomId,
                            eventFlow = getAllEventsFromFromState(roomId),
                            pushRules = pushRulesCache.pushRules,
                            existingNotifications = getAllNotifications(roomId),
                            removeStale = true,
                        )
                    notificationUpdates.apply(roomId)
                }
                notificationStore.updateState(roomId) {
                    if (it is StoredNotificationState.SyncWithoutTimeline) null
                    else it
                }
            }


            is StoredNotificationState.SyncWithTimeline -> {
                if (notificationState.notificationsDisabled) {
                    log.debug { "notification removing with $notificationState" }
                    notificationStore.deleteNotificationsByRoomId(roomId)

                    val isRead =
                        if (notificationState.isRead.needsCheck) {
                            getRelevantEventsFromTimeline(notificationState, roomId).run {
                                if (notificationState.lastRelevantEventId == null) {
                                    firstOrNull { config.lastRelevantEventFilter(it) }
                                } else {
                                    firstOrNull { it.id == notificationState.lastRelevantEventId }
                                }
                            }.let { if (it == null || it.sender == userInfo.userId) IsRead.TRUE else IsRead.FALSE }
                        } else notificationState.isRead
                    notificationStore.updateState(roomId) {
                        if (it is StoredNotificationState.SyncWithTimeline) it.copy(
                            lastProcessedEventId = notificationState.lastEventId,
                            isRead = isRead,
                        )
                        else it
                    }
                } else if (notificationState.needsProcess) {
                    log.debug { "notification processing with $notificationState" }

                    var isRead: IsRead = IsRead.TRUE
                    val eventFlow =
                        if (notificationState.isRead.needsCheck) {
                            getRelevantEventsFromTimeline(notificationState, roomId)
                                .onEach {
                                    if (it.id == notificationState.lastRelevantEventId && it.sender != userInfo.userId) {
                                        isRead = IsRead.FALSE
                                    }
                                }
                        } else {
                            isRead = notificationState.isRead
                            getRelevantEventsFromTimeline(notificationState, roomId)
                        }
                    val notificationUpdates = eventsToNotificationUpdates(
                        roomId = roomId,
                        eventFlow = eventFlow,
                        pushRules = pushRulesCache.pushRules,
                        existingNotifications = getAllNotifications(roomId),
                        removeStale = notificationState.readReceipts.isEmpty() || notificationState.lastProcessedEventId == null,
                    )
                    notificationUpdates.apply(roomId)
                    notificationStore.updateState(roomId) {
                        if (it is StoredNotificationState.SyncWithTimeline) it.copy(
                            lastProcessedEventId = notificationState.lastEventId,
                            isRead = isRead,
                        )
                        else it
                    }
                }
            }
        }
    }

    private suspend fun List<StoredNotificationUpdate>.apply(roomId: RoomId) {
        if (isNotEmpty()) {
            log.debug { "apply notification updates for $roomId" }
            transactionManager.writeTransaction {
                if (config.enableExternalNotifications) {
                    notificationStore.saveAllUpdates(this@apply.asReversed())
                }
                asReversed().forEach { update ->
                    when (update) {
                        is StoredNotificationUpdate.New -> {
                            log.trace { "new notification in $roomId ${update.id} $update" }
                            notificationStore.save(
                                update.id,
                                when (val content = update.content) {
                                    is StoredNotificationUpdate.Content.Message -> StoredNotification.Message(
                                        roomId = roomId,
                                        eventId = content.eventId,
                                        sortKey = update.sortKey,
                                        actions = update.actions,
                                    )

                                    is StoredNotificationUpdate.Content.State -> StoredNotification.State(
                                        roomId = roomId,
                                        eventId = content.eventId,
                                        type = content.type,
                                        stateKey = content.stateKey,
                                        sortKey = update.sortKey,
                                        actions = update.actions,
                                    )
                                }
                            )
                        }

                        is StoredNotificationUpdate.Update -> {
                            log.trace { "updated notification in $roomId ${update.id} $update" }
                            notificationStore.save(
                                update.id,
                                when (val content = update.content) {
                                    is StoredNotificationUpdate.Content.Message -> StoredNotification.Message(
                                        roomId = roomId,
                                        eventId = content.eventId,
                                        sortKey = update.sortKey,
                                        actions = update.actions,
                                    )

                                    is StoredNotificationUpdate.Content.State -> StoredNotification.State(
                                        roomId = roomId,
                                        eventId = content.eventId,
                                        type = content.type,
                                        stateKey = content.stateKey,
                                        sortKey = update.sortKey,
                                        actions = update.actions,
                                    )
                                }
                            )
                        }

                        is StoredNotificationUpdate.Remove -> {
                            log.trace { "removed notification in $roomId ${update.id} $update" }
                            notificationStore.delete(update.id)
                        }
                    }
                }
            }
        } else {
            log.debug { "no notification updates for $roomId" }
        }
    }

    private suspend fun getAllNotifications(roomId: RoomId) =
        notificationStore.getAll().first()
            .values.mapNotNull { it.first() }
            .filter { it.roomId == roomId }
            .associate { it.id to it.sortKey }

    private suspend fun getRelevantEventsFromTimeline(
        notificationState: StoredNotificationState.SyncWithTimeline,
        roomId: RoomId,
    ): Flow<ClientEvent.RoomEvent<*>> {
        val lastEventId = notificationState.lastEventId

        val lastProcessedEventId = notificationState.lastProcessedEventId
        if (lastProcessedEventId == lastEventId) {
            log.trace { "skip getting timeline events in $roomId, because already processed all events in $roomId" }
            return emptyFlow()
        }

        val hasStoredNotifications =
            notificationStore.getAll().first().values.mapNotNull { it.first() }
                .any { it.roomId == roomId }
        val expectedMaxNotificationCount =
            notificationState.expectedMaxNotificationCount
                .takeIf {
                    !hasStoredNotifications || // don't miss replace and redactions
                            notificationState.lastProcessedEventId == null// process has been reset and stale notifications will be removed (see eventsToNotificationUpdates)
                }

        return if (expectedMaxNotificationCount == null) {
            log.trace { "get decrypted timeline events without max notification count in $roomId" }
            roomService.getTimelineEvents(roomId, lastEventId) {
                decryptionTimeout = 2.seconds
                allowReplaceContent = false
            }.takeWhile {
                notificationState.isNotProcessedOrRead(it.first().eventId)
            }.decrypt()
        } else flow {
            var currentEventId: EventId = lastEventId
            if (!notificationState.notificationsDisabled && expectedMaxNotificationCount > 0) {
                log.trace { "get decrypted timeline events with max notification count $expectedMaxNotificationCount for notification and read processing in $roomId" }
                emitAll(
                    roomService.getTimelineEvents(roomId, currentEventId) {
                        decryptionTimeout = 2.seconds
                        maxSize = expectedMaxNotificationCount
                        allowReplaceContent = false
                    }.takeWhile {
                        currentEventId = it.first().eventId
                        notificationState.isNotProcessedOrRead(currentEventId)
                    }.decrypt()
                )
            }
            if (notificationState.isRead.needsCheck) {
                log.trace { "get not decrypted timeline events for read processing in $roomId" }
                emitAll(
                    roomService.getTimelineEvents(roomId, currentEventId) {
                        decryptionTimeout = Duration.ZERO
                        allowReplaceContent = false
                    }.takeWhile {
                        currentEventId = it.first().eventId
                        notificationState.isNotProcessedOrRead(currentEventId)
                    }.run {
                        if (notificationState.lastRelevantEventId == null) {
                            takeWhileInclusive {
                                config.lastRelevantEventFilter(it.first().event)
                            }
                        } else {
                            takeWhileInclusive {
                                it.first().eventId != notificationState.lastRelevantEventId
                            }
                        }
                    }.map { it.first().event }
                )
            }
        }
    }

    private fun StoredNotificationState.SyncWithTimeline.isNotProcessedOrRead(eventId: EventId) =
        lastProcessedEventId != eventId && !readReceipts.contains(eventId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<Flow<TimelineEvent>>.decrypt() =
        chunked(10).flatMapConcat { chunk ->
            coroutineScope {
                chunk.map { async { it.firstWithContent() } }.awaitAll().asFlow()
            }
        }.mapNotNull { it.mergedEvent?.getOrNull() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getAllEventsFromFromState(roomId: RoomId): Flow<ClientEvent.StateBaseEvent<out StateEventContent>> {
        log.debug { "process state events for notifications in $roomId" }
        return eventContentSerializerMappings.state.asFlow()
            .flatMapConcat { roomStateStore.get(roomId, it.kClass).first().values.asFlow() }
            .mapNotNull { it.first() }
    }
}