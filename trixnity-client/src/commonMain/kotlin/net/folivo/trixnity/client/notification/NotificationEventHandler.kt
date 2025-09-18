package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClientConfiguration
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
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.toList
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.unsubscribeOnCompletion
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
    private val config: MatrixClientConfiguration,
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
                    val removeRooms = allState.map { it.roomId } +
                            notificationStore.getAll().first().values.mapNotNull { it.first() }.map { it.roomId }
                    removeRooms.forEach { roomId ->
                        notificationStore.updateState(roomId) {
                            StoredNotificationState.Remove(roomId)
                        }
                    }
                }
            }
            log.trace { "skip because push rules disabled" }
            return
        }
        val allUpdatedRooms = syncEvents.syncResponse.room?.run {
            join?.filterValues {
                it.timeline?.events.isNullOrEmpty().not()
                        || it.state?.events.isNullOrEmpty().not()
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
                    }?.keys.orEmpty()
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
        val removeRooms = (completelyReadRooms + pushRulesDisabledByRoom)
        val oldPushStates =
            allState.filter { it is StoredNotificationState.Push }
                .map { it.roomId } - removeRooms.toSet() - unreadRooms.map { it.roomId }.toSet()

        if (removeRooms.isEmpty() && unreadRooms.isEmpty() && oldPushStates.isEmpty()) {
            log.trace { "skip because no changes" }
            return
        }

        if (pushRulesDisabledByRoom.isNotEmpty())
            log.debug { "schedule remove all notifications and state for push rule disabled rooms $pushRulesDisabledByRoom" }
        if (completelyReadRooms.isNotEmpty())
            log.debug { "schedule remove all notifications and state for completely read rooms $completelyReadRooms" }
        if (unreadRooms.isNotEmpty())
            log.debug { "schedule notification processing for unread rooms $unreadRooms" }
        if (oldPushStates.isNotEmpty())
            log.debug { "remove old push state: $oldPushStates" }

        transactionManager.writeTransaction {
            removeRooms.forEach { roomId ->
                notificationStore.updateState(roomId) {
                    StoredNotificationState.Remove(roomId)
                }
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
                        StoredNotificationState.SyncWithoutTimeline(roomId = roomId)
                    }
                }
            }
            oldPushStates.forEach { oldPushState ->
                notificationStore.updateState(oldPushState) { null }
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
        if (notificationState is StoredNotificationState.Push) return
        if (notificationState is StoredNotificationState.Remove && config.enableExternalNotifications.not()) {
            log.debug { "remove all notifications for $roomId" }
            notificationStore.deleteByRoomId(roomId)
            return
        }
        val notificationUpdates =
            getNotificationUpdates(
                notificationState = notificationState,
                roomId = roomId,
                currentPushRules = currentPushRules,
            )

        if (!notificationUpdates.isNullOrEmpty()) {
            log.trace { "apply notification updates for $roomId" }
            transactionManager.writeTransaction {
                if (config.enableExternalNotifications) {
                    notificationStore.saveAllUpdates(notificationUpdates)
                }
                notificationUpdates.forEach { update ->
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
        }

        if (notificationState is StoredNotificationState.SyncWithTimeline) {
            log.trace { "processed notification updates for $roomId until ${notificationState.lastEventId}" }
        }
        notificationStore.updateState(roomId) {
            when (it) {
                is StoredNotificationState.Push -> it
                is StoredNotificationState.SyncWithTimeline ->
                    if (notificationState is StoredNotificationState.SyncWithTimeline)
                        it.copy(
                            lastProcessedEventId = notificationState.lastEventId,
                            hasPush = false,
                        )
                    else it

                is StoredNotificationState.SyncWithoutTimeline ->
                    if (notificationState is StoredNotificationState.SyncWithoutTimeline) null
                    else it

                is StoredNotificationState.Remove ->
                    if (notificationState is StoredNotificationState.Remove) null
                    else it

                null -> null
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getNotificationUpdates(
        notificationState: StoredNotificationState,
        roomId: RoomId,
        currentPushRules: List<PushRule>,
    ): List<StoredNotificationUpdate>? {
        suspend fun existingNotifications() =
            notificationStore.getAll().first()
                .values.mapNotNull { it.first() }
                .filter { it.roomId == roomId }
                .associate { it.id to it.sortKey }

        return when (notificationState) {
            is StoredNotificationState.Push -> null
            is StoredNotificationState.Remove ->
                existingNotifications().map { StoredNotificationUpdate.Remove(it.key, roomId) }

            is StoredNotificationState.SyncWithTimeline -> {
                eventsToNotificationUpdates(
                    roomId = roomId,
                    eventFlow = getRelevantEventsFromTimeline(notificationState, roomId),
                    pushRules = currentPushRules,
                    existingNotifications = existingNotifications(),
                    removeStale = notificationState.lastProcessedEventId == null,
                )
            }

            is StoredNotificationState.SyncWithoutTimeline ->
                eventsToNotificationUpdates(
                    roomId = roomId,
                    eventFlow = getAllEventsFromFromState(roomId),
                    pushRules = currentPushRules,
                    existingNotifications = existingNotifications(),
                    removeStale = true,
                )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getRelevantEventsFromTimeline(
        notificationState: StoredNotificationState.SyncWithTimeline,
        roomId: RoomId
    ): Flow<ClientEvent.RoomEvent<*>> {
        val lastEventId = notificationState.lastEventId

        val lastProcessedEventId = notificationState.lastProcessedEventId
        if (lastProcessedEventId == lastEventId) return emptyFlow()

        val hasStoredNotifications =
            notificationStore.getAll().first().values.mapNotNull { it.first() }.any { it.roomId == roomId }
        val expectedMaxNotificationCount =
            (notificationState.expectedMaxNotificationCount ?: 0L)
                .takeIf { !hasStoredNotifications || lastProcessedEventId == null }

        if (expectedMaxNotificationCount == 0L) return emptyFlow()

        log.debug { "process timeline events for notifications in $roomId" }
        return roomService.getTimelineEvents(roomId, lastEventId) {
            maxSize = expectedMaxNotificationCount?.coerceAtLeast(0L)
            decryptionTimeout = 2.seconds
            allowReplaceContent = false
        }.takeWhile {
            val currentEventId = it.first().eventId
            lastProcessedEventId != currentEventId &&
                    !notificationState.readReceipts.contains(currentEventId)
        }.chunked(100).flatMapConcat { chunk ->
            coroutineScope {
                chunk.map { async { it.firstWithContent() } }.awaitAll().asFlow()
            }
        }.mapNotNull { it?.mergedEvent?.getOrNull() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getAllEventsFromFromState(roomId: RoomId): Flow<ClientEvent.StateBaseEvent<out StateEventContent>> {
        log.debug { "process state events for notifications in $roomId" }
        return eventContentSerializerMappings.state.asFlow()
            .flatMapConcat { roomStateStore.get(roomId, it.kClass).first().values.asFlow() }
            .mapNotNull { it.first() }
    }
}