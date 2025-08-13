package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.notification.NotificationService.Notification
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.get
import net.folivo.trixnity.client.store.isEncrypted
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.subscribeAsFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.NotificationService")

interface NotificationService {
    data class Notification(
        val event: ClientEvent<*>,
        val actions: Set<PushAction>,
    )

    fun getNotifications(
        decryptionTimeout: Duration = 5.seconds,
        syncResponseBufferSize: Int = 4
    ): Flow<Notification>

    fun getNotifications(
        response: Sync.Response,
        decryptionTimeout: Duration = 5.seconds,
    ): Flow<Notification>
}

class NotificationServiceImpl(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val room: RoomService,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val evaluatePushRules: EvaluatePushRules,
    private val currentSyncState: CurrentSyncState,
) : NotificationService {

    override fun getNotifications(
        response: Sync.Response,
        decryptionTimeout: Duration,
    ): Flow<Notification> = evaluateDefaultPushRules(
        extractClientEvents(response, decryptionTimeout)
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getNotifications(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int,
    ): Flow<Notification> = channelFlow {
        currentSyncState.first { it == SyncState.STARTED || it == SyncState.RUNNING }

        val clientEvents = api.sync.subscribeAsFlow(Priority.AFTER_DEFAULT)
            .buffer(syncResponseBufferSize)
            .flatMapConcat { extractClientEvents(it.syncResponse, decryptionTimeout) }

        evaluateDefaultPushRules(clientEvents).collect {
            send(it)
        }
    }.buffer(0)

    private fun extractDecryptedEvent(timelineEvent: TimelineEvent): RoomEvent<*>? {
        val originalEvent = timelineEvent.event
        val content = timelineEvent.content?.getOrNull()
        return when {
            timelineEvent.isEncrypted.not() -> originalEvent
            content == null -> null
            originalEvent is RoomEvent.MessageEvent<*> && content is MessageEventContent ->
                RoomEvent.MessageEvent(
                    content = content,
                    id = originalEvent.id,
                    sender = originalEvent.sender,
                    roomId = originalEvent.roomId,
                    originTimestamp = originalEvent.originTimestamp,
                    unsigned = originalEvent.unsigned
                )

            originalEvent is RoomEvent.StateEvent<*> && content is StateEventContent -> originalEvent
            else -> null
        }
    }

    private fun pushRulesFlow() = globalAccountDataStore.get<PushRulesEventContent>()
        .map { event ->
            event?.content?.global?.let { globalRuleSet ->
                log.trace { "global rule set: $globalRuleSet" }
                (
                        globalRuleSet.override.orEmpty() +
                                globalRuleSet.content.orEmpty() +
                                globalRuleSet.room.orEmpty() +
                                globalRuleSet.sender.orEmpty() +
                                globalRuleSet.underride.orEmpty()
                        )
            } ?: emptyList()
        }

    private fun extractInviteEventsFromSyncResponse(
        response: Sync.Response,
    ): Flow<StrippedStateEvent<*>> =
        response.room?.invite?.values
            ?.flatMap { inviteRoom ->
                inviteRoom.inviteState?.events.orEmpty()
            }
            ?.asFlow()
            ?: emptyFlow()

    private fun extractTimelineEventsFromSyncResponse(
        response: Sync.Response,
        decryptionTimeout: Duration,
    ): Flow<RoomEvent<*>> =
        room.getTimelineEvents(response, decryptionTimeout)
            .map { extractDecryptedEvent(it) }
            .filterNotNull()
            .filter {
                it.sender != userInfo.userId
            }

    private fun extractClientEvents(
        response: Sync.Response,
        decryptionTimeout: Duration,
    ): Flow<ClientEvent<*>> = merge(
        extractInviteEventsFromSyncResponse(response),
        extractTimelineEventsFromSyncResponse(response, decryptionTimeout)
    )

    private fun evaluateDefaultPushRules(
        clientEvents: Flow<ClientEvent<*>>
    ): Flow<Notification> = flow {
        coroutineScope {
            val allRules = pushRulesFlow().stateIn(this)

            clientEvents.map { event ->
                evaluatePushRules(
                    event = event,
                    allRules = allRules.value
                )?.let { event to it }
            }.filterNotNull().collect { (event, evaluatePushRulesResult) ->
                emit(Notification(event, evaluatePushRulesResult.actions))
            }

            currentCoroutineContext().cancelChildren()
        }
    }

}