package net.folivo.trixnity.client.room

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.retry
import io.kotest.assertions.timing.continually
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import io.ktor.util.*
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
import net.folivo.trixnity.client.mocks.RoomEventEncryptionServiceMock
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.room.outbox.defaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.RoomOutboxMessageStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.CustomErrorResponse
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
class OutboxMessageEventHandlerTest : ShouldSpec({
    timeout = 30_000

    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var roomEventDecryptionServiceMock: RoomEventEncryptionServiceMock
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var scope: CoroutineScope
    lateinit var currentSyncState: MutableStateFlow<SyncState>
    val json = createMatrixEventJson()
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: OutboxMessageEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        currentSyncState = MutableStateFlow(SyncState.RUNNING)
        roomStore = getInMemoryRoomStore(scope)
        roomStore.update(room) { simpleRoom }
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)
        roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock(useInput = true)
        mediaServiceMock = MediaServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = OutboxMessageEventHandler(
            MatrixClientConfiguration().apply {
                deleteSentOutboxMessageDelay = 10.seconds
            },
            api,
            listOf(roomEventDecryptionServiceMock),
            mediaServiceMock,
            roomOutboxMessageStore,
            defaultOutboxMessageMediaUploaderMappings,
            CurrentSyncState(currentSyncState),
            TransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
    }

    context(OutboxMessageEventHandler::removeOldOutboxMessages.name) {
        should("remove old outbox messages") {
            val content = RoomMessageEventContent.TextBased.Text("")
            val outbox1 = RoomOutboxMessage("transaction1", room, content)
            val outbox2 = RoomOutboxMessage("transaction2", room, content, Clock.System.now() - 11.seconds)
            val outbox3 = RoomOutboxMessage("transaction3", room, content, Clock.System.now())

            roomOutboxMessageStore.update(outbox1.transactionId) { outbox1 }
            roomOutboxMessageStore.update(outbox2.transactionId) { outbox2 }
            roomOutboxMessageStore.update(outbox3.transactionId) { outbox3 }

            eventually(3.seconds) {// we need this, because the cache may not be fast enough
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
                    RoomMessageEventContent.FileBased.Image("hi.png", url = cacheUrl)
                )
            val message2 =
                RoomOutboxMessage("transaction2", room, RoomMessageEventContent.TextBased.Text("hi"))
            roomOutboxMessageStore.update(message1.transactionId) { message1 }
            roomOutboxMessageStore.update(message2.transactionId) { message2 }
            mediaServiceMock.returnUploadMedia = Result.success(mxcUrl)
            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction1"),
                ) {
                    it shouldBe RoomMessageEventContent.FileBased.Image("hi.png", url = mxcUrl)
                    SendEventResponse(EventId("event"))
                }
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction2"),
                ) {
                    it shouldBe RoomMessageEventContent.TextBased.Text("hi")
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
            eventually(3.seconds) {// we need this, because the cache may not be fast enough
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
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextBased.Text("hi"), null)
            roomOutboxMessageStore.update(message.transactionId) { message }
            val megolmEventContent =
                MegolmEncryptedMessageEventContent(
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
            roomEventDecryptionServiceMock.returnEncrypt = Result.success(megolmEventContent)

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            eventually(3.seconds) {// we need this, because the cache may not be fast enough
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
                    RoomMessageEventContent.TextBased.Text("hi")
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
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextBased.Text("hi"), null)
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

            eventually(3.seconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldNotBe null
            }
            job.cancel()
        }
        should("not retry on MatrixServerException") {
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextBased.Text("hi"), null)
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

            eventually(3.seconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldBe null
                outboxMessages.first().sendError shouldNotBe null
            }
            job.cancel()
        }
        should("not retry on ResponseException") {
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextBased.Text("hi"), null)
            roomOutboxMessageStore.update(message.transactionId) { message }
            var call = 0
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    call++
                    when (call) {
                        1 -> throw CustomErrorResponse(HttpStatusCode.BadGateway, "error")
                        else -> SendEventResponse(EventId("event"))
                    }

                }
            }
            currentSyncState.value = SyncState.RUNNING

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            eventually(3.seconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldBe null
                outboxMessages.first().sendError shouldNotBe null
            }
            job.cancel()
        }
        should("retry on MatrixServerException rate limit") {
            val message =
                RoomOutboxMessage("transaction", room, RoomMessageEventContent.TextBased.Text("hi"), null)
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
                            ErrorResponse.LimitExceeded(),
                            300,
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
            eventually(3.seconds) {// we need this, because the cache may not be fast enough
                val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboxMessages shouldHaveSize 1
                outboxMessages.first().sentAt shouldBe null
            }
            job.cancel()
        }
    }
})