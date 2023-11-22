package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.api.client.retryOnRateLimit
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.crypto.PossiblyEncryptEvent
import net.folivo.trixnity.client.flattenNotNull
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.findUploaderOrFallback
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.RoomOutboxMessage.SendError
import net.folivo.trixnity.client.store.RoomOutboxMessageStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.retryLoopWhenSyncIs
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribeOnCompletion
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
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch(start = UNDISPATCHED) { processOutboxMessages(roomOutboxMessageStore.getAll()) }
        api.sync.subscribe(subscriber = ::removeOldOutboxMessages).unsubscribeOnCompletion(scope)
    }

    internal suspend fun removeOldOutboxMessages() {
        val outboxMessages = roomOutboxMessageStore.getAll().first().mapNotNull { it.value.first() }
        val removeOutboxMessages = outboxMessages.mapNotNull {
            // a sync means, that the message must have been received. we just give the ui a bit time to update.
            val deleteBeforeTimestamp = Clock.System.now() - 5.seconds
            if (it.sentAt != null && it.sentAt < deleteBeforeTimestamp) {
                log.debug { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                it.transactionId
            } else null
        }
        if (removeOutboxMessages.isNotEmpty())
            tm.writeTransaction {
                removeOutboxMessages.forEach { roomOutboxMessageStore.update(it) { null } }
            }
    }

    internal suspend fun processOutboxMessages(outboxMessages: Flow<Map<String, Flow<RoomOutboxMessage<*>?>>>) {
        currentSyncState.retryLoopWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
        ) {
            log.debug { "start sending outbox messages" }
            val alreadyProcessedOutboxMessages = mutableSetOf<String>()
            outboxMessages
                .map { outbox ->
                    // we need to filterKeys twice, because input and output of flattenNotNull are not in sync, and we do not want to flatten unnecessary
                    outbox.filterKeys { !alreadyProcessedOutboxMessages.contains(it) }
                }
                .flattenNotNull()
                .map { outbox ->
                    alreadyProcessedOutboxMessages.removeAll(alreadyProcessedOutboxMessages - outbox.keys)
                    outbox.filterKeys { !alreadyProcessedOutboxMessages.contains(it) }
                        .filterValues { it.sentAt == null && it.sendError == null }.values
                        .also { alreadyProcessedOutboxMessages.addAll(outbox.keys) }
                }
                .collect { outboxMessages ->
                    retryOnRateLimit {
                        val outboxMessagesGroupedByRoom = outboxMessages.groupBy { it.roomId }
                        coroutineScope {
                            outboxMessagesGroupedByRoom.forEach { (roomId, outboxMessagesInRoom) ->
                                launch {
                                    for (outboxMessage in outboxMessagesInRoom) {
                                        log.trace { "send outbox message (transactionId=${outboxMessage.transactionId}, roomId=${outboxMessage.roomId})" }
                                        val originalContent = outboxMessage.content
                                        val uploader =
                                            outboxMessageMediaUploaderMappings.findUploaderOrFallback(originalContent)
                                        val uploadedContent = try {
                                            uploader(originalContent) { cacheUri ->
                                                mediaService.uploadMedia(
                                                    cacheUri,
                                                    outboxMessage.mediaUploadProgress,
                                                    outboxMessage.keepMediaInCache,
                                                ).getOrThrow()
                                            }
                                        } catch (exception: MatrixServerException) {
                                            val sendError = when (exception.statusCode) {
                                                HttpStatusCode.Forbidden -> SendError.NoMediaPermission
                                                HttpStatusCode.PayloadTooLarge -> SendError.MediaTooLarge
                                                HttpStatusCode.BadRequest -> SendError.BadRequest(exception.errorResponse)
                                                HttpStatusCode.TooManyRequests -> throw exception
                                                else -> SendError.Unknown(exception.errorResponse)
                                            }
                                            roomOutboxMessageStore.update(outboxMessage.transactionId) {
                                                it?.copy(sendError = sendError)
                                            }
                                            continue
                                        }
                                        val eventId = try {
                                            log.debug { "encrypt and send event into $roomId" }
                                            val content = possiblyEncryptEvent(uploadedContent, roomId).getOrThrow()
                                            api.room.sendMessageEvent(roomId, content, outboxMessage.transactionId)
                                                .getOrThrow()
                                        } catch (exception: MatrixServerException) {
                                            val sendError = when (exception.statusCode) {
                                                HttpStatusCode.Forbidden -> SendError.NoEventPermission
                                                HttpStatusCode.BadRequest -> SendError.BadRequest(exception.errorResponse)
                                                HttpStatusCode.TooManyRequests -> throw exception
                                                else -> SendError.Unknown(exception.errorResponse)
                                            }
                                            roomOutboxMessageStore.update(outboxMessage.transactionId) {
                                                it?.copy(sendError = sendError)
                                            }
                                            continue
                                        }
                                        roomOutboxMessageStore.update(outboxMessage.transactionId) { it?.copy(sentAt = Clock.System.now()) }
                                        if (config.setOwnMessagesAsFullyRead) {
                                            try {
                                                api.room.setReadMarkers(roomId, eventId, eventId).getOrThrow()
                                            } catch (exception: MatrixServerException) {
                                                if (exception.statusCode == HttpStatusCode.TooManyRequests) throw exception
                                                log.warn(exception) { "could not set read marker for sent message $eventId in $roomId" }
                                            }
                                        }
                                        log.trace { "finished send outbox message (transactionId=${outboxMessage.transactionId}, roomId=${outboxMessage.roomId})" }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}