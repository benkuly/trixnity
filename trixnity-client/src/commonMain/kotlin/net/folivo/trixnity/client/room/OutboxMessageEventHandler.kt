package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.crypto.PossiblyEncryptEvent
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.RoomOutboxMessageStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class OutboxMessageEventHandler(
    private val config: MatrixClientConfiguration,
    private val api: MatrixClientServerApiClient,
    private val possiblyEncryptEvent: PossiblyEncryptEvent,
    private val mediaService: MediaService,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val outboxMessageMediaUploaderMappings: OutboxMessageMediaUploaderMappings,
    private val currentSyncState: CurrentSyncState,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch(start = UNDISPATCHED) { processOutboxMessages(roomOutboxMessageStore.getAll()) }
        api.sync.subscribeAfterSyncResponse(::removeOldOutboxMessages)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeAfterSyncResponse(::removeOldOutboxMessages)
        }
    }

    // we do this at the end of the sync, because it may be possible, that we missed events due to a gap
    internal suspend fun removeOldOutboxMessages(syncResponse: Sync.Response) {
        val outboxMessages = roomOutboxMessageStore.getAll().value
        outboxMessages.forEach {
            val deleteBeforeTimestamp = Clock.System.now() - 10.seconds
            if (it.sentAt != null && it.sentAt < deleteBeforeTimestamp) {
                log.debug { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                roomOutboxMessageStore.update(it.transactionId) { null }
            }
        }
    }

    internal suspend fun processOutboxMessages(outboxMessages: Flow<List<RoomOutboxMessage<*>>>) {
        currentSyncState.retryInfiniteWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
        ) {
            log.debug { "start sending outbox messages" }
            outboxMessages.scan(listOf<RoomOutboxMessage<*>>()) { old, new ->
                // the flow from store.roomOutboxMessage.getAll() needs some time to get updated, when one entry is updated
                // therefore we compare the lists and if they did not change, we do nothing (distinctUntilChanged)
                if (old.map { it.transactionId }.toSet() != new.map { it.transactionId }.toSet()) new
                else old
            }.distinctUntilChanged().collect { outboxMessagesList ->
                outboxMessagesList
                    .filter { it.sentAt == null && !it.reachedMaxRetryCount }
                    .forEach { outboxMessage ->
                        roomOutboxMessageStore.update(outboxMessage.transactionId) { it?.copy(retryCount = it.retryCount + 1) }
                        val roomId = outboxMessage.roomId
                        val content = outboxMessage.content
                            .let { content ->
                                val uploader =
                                    outboxMessageMediaUploaderMappings.mappings.find { it.kClass.isInstance(content) }?.uploader
                                        ?: throw IllegalArgumentException(
                                            "EventContent class ${content::class.simpleName}} is not supported by any media uploader."
                                        )
                                val uploadedContent = uploader(content) { cacheUri ->
                                    mediaService.uploadMedia(
                                        cacheUri,
                                        outboxMessage.keepMediaInCache,
                                        outboxMessage.mediaUploadProgress
                                    ).getOrThrow()
                                }
                                possiblyEncryptEvent(uploadedContent, roomId).getOrThrow()
                            }
                        log.trace { "send to $roomId : $content" }
                        val eventId =
                            api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId).getOrThrow()
                        if (config.setOwnMessagesAsFullyRead) {
                            api.rooms.setReadMarkers(roomId, eventId, eventId).getOrThrow()
                        }
                        roomOutboxMessageStore.update(outboxMessage.transactionId) { it?.copy(sentAt = Clock.System.now()) }
                        log.debug { "sent message with transactionId '${outboxMessage.transactionId}' and content $content" }
                    }
            }
        }
    }
}