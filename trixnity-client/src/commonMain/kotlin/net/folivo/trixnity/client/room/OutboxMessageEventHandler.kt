package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull.content
import net.folivo.trixnity.api.client.retryOnRateLimit
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.crypto.PossiblyEncryptEvent
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
import net.folivo.trixnity.core.EventEmitter.Priority
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
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::removeOldOutboxMessages).unsubscribeOnCompletion(scope)
    }

    internal suspend fun removeOldOutboxMessages() {
        val outboxMessages = roomOutboxMessageStore.getAll().value
        val removeOutboxMessages = outboxMessages.mapNotNull {
            // a sync means, that the message must have been received. we just give the ui a bit time to update.
            val deleteBeforeTimestamp = Clock.System.now() - 10.seconds
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

    internal suspend fun processOutboxMessages(outboxMessages: Flow<List<RoomOutboxMessage<*>>>) {
        currentSyncState.retryLoopWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
        ) {
            log.debug { "start sending outbox messages" }
            outboxMessages
                .conflate()
                .map { outbox -> outbox.filter { it.sentAt == null && it.sendError == null } }
                .collect { outboxMessagesList ->
                    retryOnRateLimit {
                        for (outboxMessage in outboxMessagesList) {
                            log.trace { "send outbox message ${outboxMessage.transactionId} into ${outboxMessage.roomId}" }
                            val roomId = outboxMessage.roomId
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
                                api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId)
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
                                    api.rooms.setReadMarkers(roomId, eventId, eventId).getOrThrow()
                                } catch (exception: MatrixServerException) {
                                    if (exception.statusCode == HttpStatusCode.TooManyRequests) throw exception
                                    log.warn(exception) { "could not set read marker for sent message $eventId in $roomId" }
                                }
                            }
                            log.debug { "sent message with transactionId '${outboxMessage.transactionId}' and content $content" }
                        }
                    }
                }
        }
    }
}