package de.connect2x.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenNotNull
import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.media.MediaTooLargeException
import de.connect2x.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import de.connect2x.trixnity.client.room.outbox.findUploaderOrFallback
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomOutboxMessage.SendError
import de.connect2x.trixnity.client.store.RoomOutboxMessageStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.client.user.UserService
import de.connect2x.trixnity.client.utils.retryLoop
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.*
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.MarkedUnreadEventContent
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.room.OutboxMessageEventHandler")

class OutboxMessageEventHandler(
    private val config: MatrixClientConfiguration,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomEventEncryptionServices: List<RoomEventEncryptionService>,
    private val userService: UserService,
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
            tm.writeTransaction {
                removeOutboxMessages.forEach { roomOutboxMessageStore.update(it.first, it.second) { null } }
            }
    }

    internal suspend fun processOutboxMessages(outboxMessages: Flow<Map<RoomOutboxMessageRepositoryKey, Flow<RoomOutboxMessage<*>?>>>) {
        currentSyncState.retryLoop(
            onError = { error, delay -> log.warn(error) { "failed sending outbox messages, try again in $delay" } },
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
                                log.trace { "start send outbox messages in $roomId" }
                                val doesRoomExist = withTimeoutOrNull(30.seconds) {
                                    roomStore.get(roomId).onEach {
                                        if (it == null) log.warn { "could not find $roomId and wait" }
                                    }.filterNotNull().first()
                                    log.info { "waited and found $roomId" }
                                } != null
                                if (doesRoomExist) {
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
                                } else {
                                    log.warn { "delete all outbox messages with $roomId because room does not exist" }
                                    roomOutboxMessageStore.deleteByRoomId(roomId)
                                }
                                log.trace { "finished send outbox messages in $roomId" }
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

    private suspend fun sendOutboxMessage(outboxMessage: RoomOutboxMessage<*>, roomId: RoomId) {
        val transactionId = outboxMessage.transactionId
        log.trace { "send outbox message (transactionId=${transactionId}, roomId=${outboxMessage.roomId})" }
        val canSendMessage = userService.canSendEvent(roomId, outboxMessage.content).first()
        if (!canSendMessage) {
            log.warn { "cannot send message, because of missing permissions in this room" }
            roomOutboxMessageStore.update(outboxMessage.roomId, transactionId) {
                it?.copy(sendError = SendError.NoEventPermission)
            }
            return
        }
        val originalContent = outboxMessage.content
        val uploader =
            outboxMessageMediaUploaderMappings.findUploaderOrFallback(originalContent)
        val uploadedContent = try {
            uploader(
                outboxMessage.mediaUploadProgress,
                originalContent
            ) { cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?> ->
                mediaService.uploadMedia(
                    cacheUri,
                    uploadProgress,
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
            roomOutboxMessageStore.update(outboxMessage.roomId, transactionId) {
                it?.copy(sendError = sendError)
            }
            return
        }
        val contentResult = roomEventEncryptionServices.encrypt(uploadedContent, roomId)

        val content = when {
            contentResult == null -> {
                log.warn { "cannot send message, because encryption algorithm not supported" }
                roomOutboxMessageStore.update(outboxMessage.roomId, transactionId) {
                    it?.copy(sendError = SendError.EncryptionAlgorithmNotSupported)
                }
                return
            }

            contentResult.isFailure -> {
                log.warn(contentResult.exceptionOrNull()) { "cannot send message" }
                roomOutboxMessageStore.update(outboxMessage.roomId, transactionId) {
                    it?.copy(sendError = SendError.EncryptionError(contentResult.exceptionOrNull()?.message))
                }
                return
            }

            else -> contentResult.getOrThrow()
        }
        val eventId = try {
            log.debug { "send outbox message ${transactionId} into $roomId" }
            api.room.sendMessageEvent(roomId, content, transactionId)
                .getOrThrow() // TODO fold as soon as continue is supported in inline lambdas
        } catch (exception: MatrixServerException) {
            val sendError = when (exception.statusCode) {
                HttpStatusCode.Forbidden -> SendError.NoEventPermission
                HttpStatusCode.BadRequest -> SendError.BadRequest(exception.errorResponse)
                HttpStatusCode.TooManyRequests -> throw exception
                else -> SendError.Unknown(exception.errorResponse)
            }
            roomOutboxMessageStore.update(outboxMessage.roomId, transactionId) {
                it?.copy(sendError = sendError)
            }
            return
        }
        roomOutboxMessageStore.update(outboxMessage.roomId, transactionId) {
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
            "finished send outbox message (transactionId=${transactionId}, roomId=${outboxMessage.roomId})"
        }
    }
}


