package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.toList
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.unsubscribeOnCompletion
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.NotificationEventHandler")

class NotificationEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomService: RoomService,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomUserStore: RoomUserStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val notificationStore: NotificationStore,
    private val eventsToNotificationUpdates: EventsToNotificationUpdates,
    private val transactionManager: TransactionManager,
    private val eventContentSerializerMappings: EventContentSerializerMappings,
    private val clock: Clock,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(subscriber = ::processSync).unsubscribeOnCompletion(scope)
        scope.launch { processNotificationStates() }
    }

    private class PushRulesCache(
        val content: PushRulesEventContent?
    ) {
        val pushRules = content?.global?.toList().orEmpty()
        val pushRulesDisabled by lazy { isPushRulesDisabled(pushRules) }
        val pushRulesDisabledByRoom by lazy { getRoomsWithDisabledPushRules(pushRules) }
    }

    private val pushRulesCache = MutableStateFlow<PushRulesCache>(PushRulesCache(null))
    private suspend fun getPushRulesCache(): PushRulesCache {
        val pushRulesEventContent = globalAccountDataStore.get<PushRulesEventContent>().first()?.content
        return pushRulesCache.updateAndGet {
            if (it.content == pushRulesEventContent) it
            else PushRulesCache(pushRulesEventContent)
        }
    }

    internal suspend fun processSync(syncEvents: SyncEvents) {
        val hasNewPushRules =
            syncEvents.syncResponse.accountData?.events?.any { it.content is PushRulesEventContent } == true
        val pushRulesCache = getPushRulesCache()
        val allState = notificationStore.getAllState().first().values.mapNotNull { it.first() }
        if (pushRulesCache.pushRulesDisabled) {
            if (hasNewPushRules) {
                log.debug { "schedule remove all notifications and state because push rules disabled" }
                transactionManager.writeTransaction {
                    allState.forEach { state ->
                        notificationStore.updateState(state.roomId) {
                            StoredNotificationState.Remove(state.roomId)
                        }
                    }
                    notificationStore.deleteAllNotifications()
                }
            }
            log.trace { "skip because push rules disabled" }
            return
        }
        val allUpdatedRooms = syncEvents.syncResponse.room?.run {
            join?.keys.orEmpty() +
                    invite?.keys.orEmpty() +
                    knock?.keys.orEmpty() +
                    leave?.keys.orEmpty()
        }.orEmpty().filterNot { roomId -> pushRulesCache.pushRulesDisabledByRoom.contains(roomId) }
            .toSet()

        data class RoomWithReadMarker(
            val roomId: RoomId,
            val readReceipts: Set<EventId>,
            val lastEventId: EventId?,
        )

        val (completelyReadRooms, unreadRooms) =
            allUpdatedRooms.map { roomId ->
                val ownReceipts = roomUserStore.getReceipts(userInfo.userId, roomId).first()?.receipts
                RoomWithReadMarker(
                    roomId = roomId,
                    readReceipts = setOfNotNull(
                        ownReceipts?.get(ReceiptType.Read)?.eventId,
                        ownReceipts?.get(ReceiptType.PrivateRead)?.eventId
                    ),
                    lastEventId = roomStore.get(roomId).first()?.lastEventId,
                )
            }.partition {
                it.lastEventId != null && it.readReceipts.contains(it.lastEventId)
            }.let { roomWithReadMarker ->
                roomWithReadMarker.first.map { it.roomId } to roomWithReadMarker.second
            }

        val pushRulesDisabledByRoom = pushRulesCache.pushRulesDisabledByRoom.takeIf { hasNewPushRules }.orEmpty()
        val removeRooms = completelyReadRooms + pushRulesDisabledByRoom

        if (removeRooms.isEmpty() && unreadRooms.isEmpty()) {
            log.trace { "skip because no changes" }
            return
        }

        if (pushRulesDisabledByRoom.isNotEmpty())
            log.debug { "schedule remove all notifications and state for push rule disabled rooms $pushRulesDisabledByRoom" }
        if (completelyReadRooms.isNotEmpty())
            log.debug { "schedule remove all notifications and state for completely read rooms $completelyReadRooms" }
        if (unreadRooms.isNotEmpty())
            log.debug { "schedule notification processing for unread rooms $unreadRooms" }
        transactionManager.writeTransaction {
            removeRooms.forEach { roomId ->
                notificationStore.updateState(roomId) {
                    StoredNotificationState.Remove(roomId)
                }
                notificationStore.deleteByRoomId(roomId)
            }
            unreadRooms.forEach { unreadRoom ->
                val roomId = unreadRoom.roomId
                val lastEventId = unreadRoom.lastEventId
                notificationStore.updateState(unreadRoom.roomId) { oldState ->
                    if (lastEventId != null) {
                        val resetProcess =
                            (oldState as? StoredNotificationState.SyncWithTimeline)?.readReceipts != unreadRoom.readReceipts
                        StoredNotificationState.SyncWithTimeline(
                            roomId = roomId,
                            hasPush = false,
                            readReceipts = unreadRoom.readReceipts,
                            lastEventId = lastEventId,
                            lastProcessedEventId = if (resetProcess) null else oldState.lastProcessedEventId,
                            expectedMaxNotificationCount = syncEvents.syncResponse.room?.join?.get(roomId)?.unreadNotifications?.notificationCount
                                ?: (oldState as? StoredNotificationState.SyncWithTimeline)?.expectedMaxNotificationCount,
                        )
                    } else {
                        StoredNotificationState.SyncWithoutTimeline(roomId = roomId, hasPush = false)
                    }
                }
            }
        }
    }

    private suspend fun processNotificationStates() {
        notificationStore.getAllState().flattenValues().collect { notificationStates ->
            val currentPushRules = getPushRulesCache().pushRules
            notificationStates.chunked(5).forEach { chunk ->
                coroutineScope {
                    chunk.forEach { notificationState ->
                        launch {
                            processNotificationState(notificationState, currentPushRules)
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun processNotificationState(
        notificationState: StoredNotificationState,
        currentPushRules: List<PushRule>,
    ) {
        val roomId = notificationState.roomId
        val notificationUpdates = when (notificationState) {
            is StoredNotificationState.Push -> null
            is StoredNotificationState.Remove -> null
            is StoredNotificationState.SyncWithTimeline -> run {
                val lastEventId = notificationState.lastEventId

                val lastProcessedEventId = notificationState.lastProcessedEventId
                if (lastProcessedEventId == lastEventId) return@run null

                val hasStoredNotifications =
                    notificationStore.getAll().first().values.mapNotNull { it.first() }.any { it.roomId == roomId }
                val expectedMaxNotificationCount =
                    (notificationState.expectedMaxNotificationCount?.toInt() ?: 0)
                        .takeIf { !hasStoredNotifications || lastProcessedEventId == null }

                if (expectedMaxNotificationCount == 0) return@run null

                log.debug { "process timeline events for notifications in $roomId" }
                roomService.getTimelineEvents(roomId, lastEventId) {
                    decryptionTimeout = 2.seconds
                    allowReplaceContent = false
                }.take(expectedMaxNotificationCount?.coerceAtLeast(0) ?: Int.MAX_VALUE)
                    .takeWhile {
                        val currentEventId = it.first().eventId
                        lastProcessedEventId != currentEventId &&
                                !notificationState.readReceipts.contains(currentEventId)
                    }.chunked(100).flatMapConcat { chunk ->
                        coroutineScope {
                            chunk.map { async { it.firstWithContent() } }.awaitAll().asFlow()
                        }
                    }.mapNotNull { it?.mergedEvent?.getOrNull() }
            }

            is StoredNotificationState.SyncWithoutTimeline -> {
                log.debug { "process state events for notifications in $roomId" }
                eventContentSerializerMappings.state.asFlow()
                    .flatMapConcat { roomStateStore.get(roomId, it.kClass).first().values.asFlow() }
                    .mapNotNull { it.first() }

            }
        }?.let {
            eventsToNotificationUpdates(
                eventFlow = it,
                pushRules = currentPushRules
            )
        }?.toList()

        if (notificationState is StoredNotificationState.Remove) {
            log.debug { "remove all notifications for $roomId" }
            notificationStore.deleteByRoomId(roomId)
        } else {
            val sortKeyPrefix by lazy { "$roomId-${clock.now()}" }
            var index = UInt.MAX_VALUE
            fun sortKey() = "$sortKeyPrefix-${(index--).toHexString()}"

            val staleNotifications =
                if (notificationState is StoredNotificationState.SyncWithTimeline && notificationState.lastProcessedEventId == null ||
                    notificationState is StoredNotificationState.SyncWithoutTimeline
                ) {
                    notificationStore.getAll().first().values.mapNotNull { it.first() }
                        .filter { it.roomId == roomId }.map { it.id } -
                            notificationUpdates.orEmpty().map { it.id }.toSet()
                } else null

            if (!notificationUpdates.isNullOrEmpty() || !staleNotifications.isNullOrEmpty()) {
                transactionManager.writeTransaction {
                    notificationUpdates?.forEach { update ->
                        when (val change = update.change) {
                            is NotificationUpdate.Change.New -> {
                                log.trace { "new notification ${update.id} $update" }
                                notificationStore.set(
                                    update.id,
                                    when (update) {
                                        is NotificationUpdate.Message -> StoredNotification.Message(
                                            roomId = roomId,
                                            eventId = update.eventId,
                                            sortKey = sortKey(),
                                            actions = change.actions,
                                        )

                                        is NotificationUpdate.State -> StoredNotification.State(
                                            roomId = roomId,
                                            eventId = update.eventId,
                                            type = update.type,
                                            stateKey = update.stateKey,
                                            sortKey = sortKey(),
                                            actions = change.actions,
                                        )
                                    }
                                )
                            }

                            is NotificationUpdate.Change.Update -> {
                                log.trace { "updated notification ${update.id} $update" }
                                notificationStore.update(update.id) {
                                    when (update) {
                                        is NotificationUpdate.Message -> StoredNotification.Message(
                                            roomId = roomId,
                                            eventId = update.eventId,
                                            sortKey = it?.sortKey ?: sortKey(),
                                            actions = change.actions,
                                        )

                                        is NotificationUpdate.State -> StoredNotification.State(
                                            roomId = roomId,
                                            eventId = update.eventId,
                                            type = update.type,
                                            stateKey = update.stateKey,
                                            sortKey = it?.sortKey ?: sortKey(),
                                            actions = change.actions,
                                        )
                                    }
                                }
                            }

                            is NotificationUpdate.Change.Remove -> {
                                log.trace { "removed notification ${update.id} $update" }
                                notificationStore.delete(update.id)
                            }
                        }
                    }
                    staleNotifications?.forEach { id ->
                        log.trace { "removed stale notification $id" }
                        notificationStore.delete(id)
                    }
                }
            }
        }

        notificationStore.updateState(roomId) {
            when (it) {
                is StoredNotificationState.Push -> it
                is StoredNotificationState.SyncWithTimeline -> it.copy(
                    lastProcessedEventId = it.lastEventId,
                    hasPush = false,
                )

                is StoredNotificationState.SyncWithoutTimeline -> null
                is StoredNotificationState.Remove -> null
                null -> null
            }
        }
    }
}