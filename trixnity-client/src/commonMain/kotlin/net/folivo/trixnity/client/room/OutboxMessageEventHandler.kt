package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenNotNull
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.media.MediaTooLargeException
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.findUploaderOrFallback
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.RoomOutboxMessage.SendError
import net.folivo.trixnity.client.store.RoomOutboxMessageStore
import net.folivo.trixnity.client.store.TransactionManager
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.client.utils.retryLoopWhenSyncIs
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.MarkedUnreadEventContent

private val log = KotlinLogging.logger {}

class OutboxMessageEventHandler(
    private val config: MatrixClientConfiguration,
    private val api: MatrixClientServerApiClient,
    private val roomEventEncryptionServices: List<RoomEventEncryptionService>,
    private val mediaService: MediaService,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val outboxMessageMediaUploaderMappings: OutboxMessageMediaUploaderMappings,
    private val currentSyncState: CurrentSyncState,
    private val userInfo: UserInfo,
    private val tm: TransactionManager,
    private val clock: Clock,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        scope.launch(start = UNDISPATCHED) { processOutboxMessages(roomOutboxMessageStore.getAll()) }
        api.sync.subscribe(subscriber = ::removeOldOutboxMessages).unsubscribeOnCompletion(scope)
    }

    internal suspend fun removeOldOutboxMessages() {
        val outboxMessages = roomOutboxMessageStore.getAll().first().mapNotNull { it.value.first() }
        val removeOutboxMessages = outboxMessages.mapNotNull {
            // a sync means, that the message must have been received. we just give the ui a bit time to update.
            val deleteBeforeTimestamp = clock.now() - config.deleteSentOutboxMessageDelay
            if (it.sentAt != null && it.sentAt < deleteBeforeTimestamp) {
                log.debug { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                it.roomId to it.transactionId
            } else null
        }
        if (removeOutboxMessages.isNotEmpty())
            tm.transaction {
                removeOutboxMessages.forEach { roomOutboxMessageStore.update(it.first, it.second) { null } }
            }
    }

    internal suspend fun processOutboxMessages(outboxMessages: Flow<Map<RoomOutboxMessageRepositoryKey, Flow<RoomOutboxMessage<*>?>>>) {
        currentSyncState.retryLoopWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
        ) {
            log.debug { "start sending outbox messages" }
            val alreadyProcessedOutboxMessages = mutableSetOf<RoomOutboxMessageRepositoryKey>()
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
                    val outboxMessagesGroupedByRoom = outboxMessages
                        .groupBy { it.roomId }
                        .mapValues { m -> m.value.sortedBy { it.createdAt } }
                    coroutineScope {
                        outboxMessagesGroupedByRoom.forEach { (roomId, outboxMessagesInRoom) ->
                            launch {
                                for (outboxMessage in outboxMessagesInRoom) {
                                    val sendMessage = async { sendOutboxMessage(outboxMessage, roomId) }
                                    val checkCancelled = async { checkWhetherCancelled(outboxMessage) }
                                    select {
                                        sendMessage.onAwait {}
                                        checkCancelled.onAwait {}
                                    }
                                    sendMessage.cancel()
                                    checkCancelled.cancel()
                                }
                            }
                        }
                    }
                }
        }
    }


    private suspend fun checkWhetherCancelled(outboxMessage: RoomOutboxMessage<*>) {
        roomOutboxMessageStore.getAsFlow(outboxMessage.roomId, outboxMessage.transactionId).first { it == null }
        log.debug { "cancel sending of ${outboxMessage.transactionId}" }
    }

    private suspend fun sendOutboxMessage(outboxMessage: RoomOutboxMessage<*>, roomId: RoomId) = coroutineScope {
        log.trace { "send outbox message (transactionId=${outboxMessage.transactionId}, roomId=${outboxMessage.roomId})" }
        val originalContent = outboxMessage.content
        val uploader =
            outboxMessageMediaUploaderMappings.findUploaderOrFallback(originalContent)
        uploader?.let {
            launch {
                it.uploadProgress.collectLatest { outboxMessage.mediaUploadProgress.value = it }
            }
        }
        val uploadedContent = try {
            uploader?.uploader(originalContent) { cacheUri: String, upload ->
                mediaService.uploadMedia(
                    cacheUri,
                    upload,
                    outboxMessage.keepMediaInCache,
                ).getOrThrow()
            }
        } catch (exception: Exception) {
            val sendError = when (exception) {
                is MatrixServerException -> when (exception.statusCode) {
                    HttpStatusCode.Forbidden -> SendError.NoMediaPermission
                    HttpStatusCode.PayloadTooLarge -> SendError.MediaTooLarge
                    HttpStatusCode.BadRequest -> SendError.BadRequest(exception.errorResponse)
                    HttpStatusCode.TooManyRequests -> throw exception
                    else -> {
                        log.error(exception) { "could not upload media" }
                        SendError.Unknown(exception.errorResponse)
                    }
                }

                is MediaTooLargeException -> SendError.MediaTooLarge
                else -> {
                    log.error(exception) { "could not upload media" }
                    throw exception
                }
            }
            roomOutboxMessageStore.update(outboxMessage.roomId, outboxMessage.transactionId) {
                it?.copy(sendError = sendError)
            }
            return@coroutineScope
        }
        if (uploadedContent != null) {
            val contentResult = roomEventEncryptionServices.encrypt(uploadedContent, roomId)

            val content = when {
                contentResult == null -> {
                    log.warn { "cannot send message, because encryption algorithm not supported" }
                    roomOutboxMessageStore.update(outboxMessage.roomId, outboxMessage.transactionId) {
                        it?.copy(sendError = SendError.EncryptionAlgorithmNotSupported)
                    }
                    return@coroutineScope
                }

                contentResult.isFailure -> {
                    log.warn(contentResult.exceptionOrNull()) { "cannot send message" }
                    roomOutboxMessageStore.update(outboxMessage.roomId, outboxMessage.transactionId) {
                        it?.copy(sendError = SendError.EncryptionError(contentResult.exceptionOrNull()?.message))
                    }
                    return@coroutineScope
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
                roomOutboxMessageStore.update(outboxMessage.roomId, outboxMessage.transactionId) {
                    it?.copy(sendError = sendError)
                }
                return@coroutineScope
            }
            roomOutboxMessageStore.update(outboxMessage.roomId, outboxMessage.transactionId) {
                it?.copy(sentAt = clock.now(), eventId = eventId)
            }
            if (config.markOwnMessageAsRead) {
                coroutineScope {
                    launch {
                        api.room.setReadMarkers(roomId, eventId, eventId)
                            .onFailure { exception ->
                                log.warn(exception) { "could not set read marker for sent message $eventId in $roomId" }
                            }
                    }
                    launch {
                        api.room.setAccountData(MarkedUnreadEventContent(false), roomId, userInfo.userId)
                            .onFailure { exception ->
                                log.warn(exception) { "could not reset unread for sent message $eventId in $roomId" }
                            }
                    }
                }
            }
            log.trace {
                "finished send outbox message (transactionId=${outboxMessage.transactionId}, roomId=${outboxMessage.roomId})"
            }
        }
    }
}


