package net.folivo.trixnity.client.room

import io.kotest.assertions.retry
import io.kotest.assertions.timing.continually
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.PossiblyEncryptEventMock
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.room.outbox.defaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.RoomOutboxMessageStore
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OutboxMessageEventHandlerTest : ShouldSpec({
    timeout = 30_000

    val room = simpleRoom.roomId
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var possiblyEncryptEventMock: PossiblyEncryptEventMock
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var scope: CoroutineScope
    lateinit var currentSyncState: MutableStateFlow<SyncState>
    val json = createMatrixEventJson()
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: OutboxMessageEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        currentSyncState = MutableStateFlow(SyncState.RUNNING)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)
        possiblyEncryptEventMock = PossiblyEncryptEventMock()
        possiblyEncryptEventMock.returnEncryptMegolm = { it }
        mediaServiceMock = MediaServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = OutboxMessageEventHandler(
            MatrixClientConfiguration(),
            api,
            possiblyEncryptEventMock,
            mediaServiceMock,
            roomOutboxMessageStore,
            defaultOutboxMessageMediaUploaderMappings,
            CurrentSyncState(currentSyncState),
            RepositoryTransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
    }

    context(OutboxMessageEventHandler::removeOldOutboxMessages.name) {
        should("remove old outbox messages") {
            val content = RoomMessageEventContent.TextMessageEventContent("")
            val outbox1 = RoomOutboxMessage("transaction1", room, content)
            val outbox2 = RoomOutboxMessage("transaction2", room, content, Clock.System.now() - 10.seconds)
            val outbox3 = RoomOutboxMessage("transaction3", room, content, Clock.System.now())

            roomOutboxMessageStore.update(outbox1.transactionId) { outbox1 }
            roomOutboxMessageStore.update(outbox2.transactionId) { outbox2 }
            roomOutboxMessageStore.update(outbox3.transactionId) { outbox3 }

            retry(100, 1.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                cut.removeOldOutboxMessages()
                roomOutboxMessageStore.getAll().flattenValues().first() shouldContainExactly listOf(outbox1, outbox3)
            }
        }
    }
    context(OutboxMessageEventHandler::processOutboxMessages.name) {
        should("wait until connected, upload media, send message and mark outbox message as sent") {
            val mxcUrl = "mxc://dino"
            val cacheUrl = "cache://unicorn"
            val message1 =
                RoomOutboxMessage(
                    "transaction1",
                    room,
                    RoomMessageEventContent.ImageMessageEventContent("hi.png", url = cacheUrl)
                )
            val message2 =
                RoomOutboxMessage("transaction2", room, RoomMessageEventContent.TextMessageEventContent("hi"))
            roomOutboxMessageStore.update(message1.transactionId) { message1 }
            roomOutboxMessageStore.update(message2.transactionId) { message2 }
            mediaServiceMock.returnUploadMedia = Result.success(mxcUrl)
            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction1"),
                ) {
                    it shouldBe RoomMessageEventContent.ImageMessageEventContent("hi.png", url = mxcUrl)
                    SendEventResponse(EventId("event"))
                }
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction2"),
                ) {
                    it shouldBe RoomMessageEventContent.TextMessageEventContent("hi")
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }
            currentSyncState.value = SyncState.STARTED

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            until(50.milliseconds) {
                job.isActive
            }
            currentSyncState.value = SyncState.RUNNING
            mediaServiceMock.uploadMediaCalled.first { it == cacheUrl }
            retry(100, 1.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 2
                outboxMessages[0].sentAt shouldNotBe null
                outboxMessages[1].sentAt shouldNotBe null
            }
            sendMessageEventCalled shouldBe true
            job.cancel()
        }
        should("encrypt events in encrypted rooms") {
            currentSyncState.value = SyncState.RUNNING
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextMessageEventContent("hi"), null)
            roomOutboxMessageStore.update(message.transactionId) { message }
            val megolmEventContent =
                EncryptedEventContent.MegolmEncryptedEventContent(
                    "cipher",
                    Key.Curve25519Key(null, "key"),
                    "device",
                    "session"
                )
            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.encrypted", "transaction"),
                ) {
                    it shouldBe megolmEventContent
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }
            possiblyEncryptEventMock.returnEncryptMegolm = { megolmEventContent }

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            retry(100, 1.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldNotBe null
            }
            sendMessageEventCalled shouldBe true
            job.cancel()
        }
        should("not send messages multiple times") {
            val message =
                RoomOutboxMessage(
                    "transaction1",
                    room,
                    RoomMessageEventContent.TextMessageEventContent("hi")
                )
            val sendMessageEventCalled = MutableStateFlow(0)
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction1"),
                ) {
                    sendMessageEventCalled.value++
                    SendEventResponse(EventId("event"))
                }
            }
            currentSyncState.value = SyncState.RUNNING

            val job = launch(Dispatchers.Default) {
                cut.processOutboxMessages(
                    flowOf(
                        mapOf(message.transactionId to flowOf(message)),
                        mapOf(message.transactionId to flowOf(message)),
                    )
                )
            }

            until(50.milliseconds) {
                job.isActive
            }
            currentSyncState.value = SyncState.RUNNING
            sendMessageEventCalled.first { it == 1 }
            continually(50.milliseconds) {
                sendMessageEventCalled.value shouldBe 1
            }

            job.cancel()
        }
        should("retry on sending error") {
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextMessageEventContent("hi"), null)
            roomOutboxMessageStore.update(message.transactionId) { message }
            var call = 0
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    call++
                    when (call) {
                        1 -> throw RuntimeException("http send failure")
                        else -> SendEventResponse(EventId("event"))
                    }

                }
            }
            currentSyncState.value = SyncState.RUNNING

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            retry(100, 1.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldNotBe null
            }
            job.cancel()
        }
        should("not retry on MatrixServerException") {
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextMessageEventContent("hi"), null)
            roomOutboxMessageStore.update(message.transactionId) { message }
            var call = 0
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    call++
                    when (call) {
                        1 -> throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown())
                        else -> SendEventResponse(EventId("event"))
                    }

                }
            }
            currentSyncState.value = SyncState.RUNNING

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            retry(100, 1.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldBe null
            }
            job.cancel()
        }
        should("retry on MatrixServerException rate limit") {
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextMessageEventContent("hi"), null)
            roomOutboxMessageStore.update(message.transactionId) { message }
            var call = 0
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    call++
                    when (call) {
                        1 -> throw MatrixServerException(
                            HttpStatusCode.TooManyRequests,
                            ErrorResponse.LimitExceeded(retryAfterMillis = 300)
                        )

                        else -> SendEventResponse(EventId("event"))
                    }

                }
            }
            currentSyncState.value = SyncState.RUNNING

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            retry(100, 200.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldBe null
            }
            retry(100, 1.seconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldBe null
            }
            job.cancel()
        }
    }
})