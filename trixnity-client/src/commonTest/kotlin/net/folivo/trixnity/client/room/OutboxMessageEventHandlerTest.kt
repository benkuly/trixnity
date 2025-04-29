package net.folivo.trixnity.client.room

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import io.ktor.http.ContentType.Image.PNG
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventEncryptionServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.room.message.image
import net.folivo.trixnity.client.room.outbox.defaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.testutils.CustomErrorResponse
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OutboxMessageEventHandlerTest : TrixnityBaseTest() {

    init {
        testScope.testScheduler.advanceTimeBy(10.days)
    }

    private val room = simpleRoom.roomId

    private val currentSyncState = MutableStateFlow(SyncState.RUNNING)
    private val roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock(useInput = true)
    private val mediaServiceMock = MediaServiceMock()
    private val roomServiceMock = RoomServiceMock().apply {
        rooms.value = mapOf(room to MutableStateFlow(Room(room)))
    }

    private val roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore()


    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)

    private val cut = OutboxMessageEventHandler(
        MatrixClientConfiguration().apply {
            deleteSentOutboxMessageDelay = 10.seconds
        },
        api,
        listOf(roomEventDecryptionServiceMock),
        mediaServiceMock,
        roomOutboxMessageStore,
        defaultOutboxMessageMediaUploaderMappings,
        CurrentSyncState(currentSyncState),
        UserInfo(UserId("user", "server"), "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        TransactionManagerMock(),
        testScope.testClock,
    )

    @Test
    fun `removeOldOutboxMessages » remove old outbox messages`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("")
        val outbox1 = RoomOutboxMessage(room, "transaction1", content, testClock.now())
        val outbox2 =
            RoomOutboxMessage(room, "transaction2", content, testClock.now(), testClock.now() - 11.seconds)
        val outbox3 = RoomOutboxMessage(room, "transaction3", content, testClock.now(), testClock.now())

        with(roomOutboxMessageStore) {
            update(outbox1.roomId, outbox1.transactionId) { outbox1 }
            update(outbox2.roomId, outbox2.transactionId) { outbox2 }
            update(outbox3.roomId, outbox3.transactionId) { outbox3 }
        }

        eventually(3.seconds) {// we need this, because the cache may not be fast enough
            cut.removeOldOutboxMessages()
            roomOutboxMessageStore.getAll().flattenValues().first() shouldContainExactly listOf(outbox1, outbox3)
        }
    }

    @Test
    fun `processOutboxMessages » wait until connected upload media send message and mark outbox message as sent`() =
        runTest {
            val mxcUrl = "mxc://dino"
            val cacheUrl = "cache://unicorn"
            val message1 =
                RoomOutboxMessage(
                    room,
                    "transaction1",
                    RoomMessageEventContent.FileBased.Image("hi.png", url = cacheUrl),
                    testClock.now(),
                )
            val message2 =
                RoomOutboxMessage(
                    room,
                    "transaction2",
                    RoomMessageEventContent.TextBased.Text("hi"),
                    testClock.now()
                )

            with(roomOutboxMessageStore) {
                update(message1.roomId, message1.transactionId) { message1 }
                update(message2.roomId, message2.transactionId) { message2 }
            }

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

            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                cut.processOutboxMessages(roomOutboxMessageStore.getAll())
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
        }

    @Test
    fun `processOutboxMessages » encrypt events in encrypted rooms`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val message =
            RoomOutboxMessage(room, "transaction", RoomMessageEventContent.TextBased.Text("hi"), testClock.now())
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        val megolmEventContent =
            MegolmEncryptedMessageEventContent(
                "cipher",
                Curve25519KeyValue("key"),
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

        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        eventually(3.seconds) {// we need this, because the cache may not be fast enough
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sentAt shouldNotBe null
        }
        sendMessageEventCalled shouldBe true
    }

    @Test
    fun `processOutboxMessages » not send messages multiple times`() = runTest {
        val message =
            RoomOutboxMessage(
                room,
                "transaction1",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now(),
            )
        val sendMessageEventCalled = MutableStateFlow(0)
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "transaction1"),
            ) {
                sendMessageEventCalled.value++
                SendEventResponse(EventId("event"))
            }
        }
        currentSyncState.value = SyncState.RUNNING

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cut.processOutboxMessages(
                flowOf(
                    mapOf(RoomOutboxMessageRepositoryKey(message.roomId, message.transactionId) to flowOf(message)),
                    mapOf(RoomOutboxMessageRepositoryKey(message.roomId, message.transactionId) to flowOf(message)),
                )
            )
        }

        currentSyncState.value = SyncState.RUNNING
        sendMessageEventCalled.first { it == 1 }
        continually(50.milliseconds) {
            sendMessageEventCalled.value shouldBe 1
        }
    }

    @Test
    fun `processOutboxMessages » retry on sending error`() = runTest {
        val message =
            RoomOutboxMessage(room, "transaction", RoomMessageEventContent.TextBased.Text("hi"), testClock.now())
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
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

        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        eventually(3.seconds) {// we need this, because the cache may not be fast enough
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sentAt shouldNotBe null
        }
    }

    @Test
    fun `processOutboxMessages » not retry on MatrixServerException`() = runTest {
        val message =
            RoomOutboxMessage(room, "transaction", RoomMessageEventContent.TextBased.Text("hi"), testClock.now())
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        var call = 0
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "transaction"),
            ) {
                call++
                when (call) {
                    1 -> throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown(""))
                    else -> SendEventResponse(EventId("event"))
                }

            }
        }
        currentSyncState.value = SyncState.RUNNING

        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        eventually(3.seconds) {// we need this, because the cache may not be fast enough
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sentAt shouldBe null
            outboxMessages.first().sendError shouldNotBe null
        }
    }

    @Test
    fun `processOutboxMessages » not retry on ResponseException`() = runTest {
        val message =
            RoomOutboxMessage(room, "transaction", RoomMessageEventContent.TextBased.Text("hi"), testClock.now())
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
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

        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        eventually(3.seconds) {// we need this, because the cache may not be fast enough
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sentAt shouldBe null
            outboxMessages.first().sendError shouldNotBe null
        }
    }

    @Test
    fun `processOutboxMessages » retry on MatrixServerException rate limit`() = runTest {
        val message =
            RoomOutboxMessage(room, "transaction", RoomMessageEventContent.TextBased.Text("hi"), testClock.now())
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        var call = 0
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "transaction"),
            ) {
                call++
                when (call) {
                    1 -> throw MatrixServerException(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse.LimitExceeded(""),
                        300,
                    )

                    else -> SendEventResponse(EventId("event"))
                }

            }
        }
        currentSyncState.value = SyncState.RUNNING

        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

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
    }

    @Test
    fun `processOutboxMessages » not send message if sending was cancelled during upload`() = runTest {
        val mxcUrl = "mxc://dino"
        val cacheUrl = "cache://unicorn"
        val message1 =
            RoomOutboxMessage(
                room,
                "abortedMessage",
                RoomMessageEventContent.FileBased.Image("hi.png", url = cacheUrl),
                testClock.now(),
            )
        val message2 =
            RoomOutboxMessage(
                room,
                "unabortedMessage",
                RoomMessageEventContent.TextBased.Text("Nachricht"),
                testClock.now(),
            )

        with(roomOutboxMessageStore) {
            deleteAll()
            update(message1.roomId, message1.transactionId) { message1 }
            update(message2.roomId, message2.transactionId) { message2 }
        }

        mediaServiceMock.returnUploadMedia = Result.success(mxcUrl)
        mediaServiceMock.uploadTimer.value = 3_000
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "abortedMessage"),
            ) {
                it shouldBe RoomMessageEventContent.FileBased.Image("hi.png", url = mxcUrl)
                SendEventResponse(EventId("event"))
            }
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "unabortedMessage"),
            )
            {
                it shouldBe RoomMessageEventContent.TextBased.Text("Nachricht")
                SendEventResponse(EventId("event"))
            }
        }
        currentSyncState.value = SyncState.STARTED

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(100)
            roomOutboxMessageStore.update(message1.roomId, message1.transactionId) { null }
        }
        currentSyncState.value = SyncState.RUNNING
        mediaServiceMock.uploadMediaCalled.first { it == cacheUrl }

        eventually(5.seconds) {// we need this, because the cache may not be fast enough
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages[0].sentAt shouldNotBe null
        }
    }

    @Test
    fun `processOutboxMessages » monitor upload on a file with thumbnail correctly`() = runTest {
        val thumbnailInfo = ThumbnailInfo(15, 15, "image/png", 10)
        mediaServiceMock.uploadTimer.value = 100
        mediaServiceMock.returnPrepareUploadMedia.add("mediaCacheUrl")
        mediaServiceMock.returnPrepareUploadMedia.add("thumbnailCacheUrl")
        mediaServiceMock.returnUploadMedia = Result.success("mxc://success")
        val message = MessageBuilder(room, roomServiceMock, mediaServiceMock, UserId("")).build {
            image(
                body = "image.png",
                image = "fake_image_with_Thumbnail".toByteArray().toByteArrayFlow(),
                format = null,
                formattedBody = null,
                fileName = null,
                type = PNG,
                size = 25,
                height = 1024,
                width = 1024,
                thumbnail = "fake_Thumbnail".toByteArray().toByteArrayFlow(),
                thumbnailInfo = thumbnailInfo,
            )
        }
        message shouldNotBe null
        val message1 = RoomOutboxMessage(
            room, "message with thumbnail", message as MessageEventContent,
            testClock.now()
        )
        roomOutboxMessageStore.update(message1.roomId, message1.transactionId) { message1 }
        var sendEventCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "message with thumbnail"),
            ) {
                sendEventCalled = true
                SendEventResponse(EventId("event"))
            }
        }
        currentSyncState.value = SyncState.STARTED
        mediaServiceMock.uploadSizes.value = ArrayList<Long>().apply {
            val content = message1.content as RoomMessageEventContent.FileBased.Image
            this.add(content.info?.thumbnailInfo?.size ?: 0)
            this.add(content.info?.size ?: 0)
        }

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cut.processOutboxMessages(roomOutboxMessageStore.getAll())
        }

        currentSyncState.value = SyncState.RUNNING
        eventually(3.seconds) {
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages[0].mediaUploadProgress.value shouldBe FileTransferProgress(0, 35)
        }
        eventually(3.seconds) {
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages[0].mediaUploadProgress.value shouldBe FileTransferProgress(35, 35)
        }
        eventually(3.seconds) {
            sendEventCalled shouldBe true
        }
    }

}