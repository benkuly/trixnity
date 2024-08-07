package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.selects.select
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenNotNull
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.findUploaderOrFallback
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.RoomOutboxMessage.SendError
import net.folivo.trixnity.client.store.RoomOutboxMessageStore
import net.folivo.trixnity.client.store.TransactionManager
import net.folivo.trixnity.client.utils.retryLoopWhenSyncIs
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class OutboxMessageEventHandler(
    private val config: MatrixClientConfiguration,
    private val api: MatrixClientServerApiClient,
    private val roomEventEncryptionServices: List<RoomEventEncryptionService>,
    private val mediaService: MediaService,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val outboxMessageMediaUploaderMappings: OutboxMessageMediaUploaderMappings,
    private val currentSyncState: CurrentSyncState,
    private val tm: TransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch(start = UNDISPATCHED) { processOutboxMessages(roomOutboxMessageStore.getAll()) }
        api.sync.subscribe(subscriber = ::removeOldOutboxMessages).unsubscribeOnCompletion(scope)
    }

    internal suspend fun removeOldOutboxMessages() {
        val outboxMessages = roomOutboxMessageStore.getAll().first().mapNotNull { it.value.first() }
        val removeOutboxMessages = outboxMessages.mapNotNull {
            // a sync means, that the message must have been received. we just give the ui a bit time to update.
            val deleteBeforeTimestamp = Clock.System.now() - config.deleteSentOutboxMessageDelay
            if (it.sentAt != null && it.sentAt < deleteBeforeTimestamp) {
                log.debug { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                it.transactionId
            } else null
        }
        if (removeOutboxMessages.isNotEmpty())
            tm.transaction {
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
                    val outboxMessagesGroupedByRoom = outboxMessages.groupBy { it.roomId }
                    coroutineScope {
                        outboxMessagesGroupedByRoom.forEach { (roomId, outboxMessagesInRoom) ->
                            launch {
                                for (outboxMessage in outboxMessagesInRoom) {
                                    val sendMessage = async { sendOutboxMessage(outboxMessage, roomId) }
                                    val checkAborted = async { checkWhetherAborted(outboxMessage) }
                                    select {
                                        sendMessage.onAwait{}
                                        checkAborted.onAwait{}
                                    }
                                    if (sendMessage.isActive) sendMessage.cancel()
                                    if (checkAborted.isActive) checkAborted.cancel()
                                }
                            }
                        }
                    }
                }
        }
    }


    private suspend fun checkWhetherAborted(outboxMessage: RoomOutboxMessage<*>) {
        roomOutboxMessageStore.getAsFlow(outboxMessage.transactionId).first { it == null }
        log.debug { "abort sending of ${outboxMessage.transactionId}" }
    }

    private suspend fun sendOutboxMessage(outboxMessage: RoomOutboxMessage<*>, roomId: RoomId) {
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
                else -> {
                    log.error(exception) { "could not upload media" }
                    SendError.Unknown(exception.errorResponse)
                }
            }
            roomOutboxMessageStore.update(outboxMessage.transactionId) {
                it?.copy(sendError = sendError)
            }
            return
        }
        val contentResult = roomEventEncryptionServices.encrypt(uploadedContent, roomId)

        val content = when {
            contentResult == null -> {
                log.warn { "cannot send message, because encryption algorithm not supported" }
                roomOutboxMessageStore.update(outboxMessage.transactionId) {
                    it?.copy(sendError = SendError.EncryptionAlgorithmNotSupported)
                }
                return
            }

            contentResult.isFailure -> {
                log.warn(contentResult.exceptionOrNull()) { "cannot send message" }
                roomOutboxMessageStore.update(outboxMessage.transactionId) {
                    it?.copy(sendError = SendError.EncryptionError(contentResult.exceptionOrNull()?.message))
                }
                return
            }

            else -> contentResult.getOrThrow()
        }
        val eventId = try {
            log.debug { "send event into $roomId" }
            api.room.sendMessageEvent(roomId, content, outboxMessage.transactionId)
                .getOrThrow() // TODO fold as soon as continue is supported in inline lambdas
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
            return
        }
        roomOutboxMessageStore.update(outboxMessage.transactionId) {
            it?.copy(sentAt = Clock.System.now(), eventId = eventId)
        }
        if (config.setOwnMessagesAsFullyRead) {
            try {
                api.room.setReadMarkers(roomId, eventId, eventId).getOrThrow()
            } catch (exception: ResponseException) {
                if (exception.response.status == HttpStatusCode.TooManyRequests) throw exception
                log.warn(exception) { "could not set read marker for sent message $eventId in $roomId" }
            }
        }
        log.trace {
            "finished send outbox message (transactionId=${outboxMessage.transactionId}, roomId=${outboxMessage.roomId})"
        }
    }
}


