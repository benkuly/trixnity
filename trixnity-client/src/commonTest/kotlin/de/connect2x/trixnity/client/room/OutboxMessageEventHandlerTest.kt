package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.continually
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.getInMemoryRoomOutboxMessageStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.MediaServiceMock
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.mocks.UserServiceMock
import de.connect2x.trixnity.client.room.message.MessageBuilder
import de.connect2x.trixnity.client.room.message.image
import de.connect2x.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import de.connect2x.trixnity.client.room.outbox.default
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.clientserverapi.model.room.SendEventResponse
import de.connect2x.trixnity.clientserverapi.model.room.SendMessageEvent
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.ThumbnailInfo
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.CustomErrorResponse
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import de.connect2x.trixnity.utils.toByteArrayFlow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    private val userService = UserServiceMock().apply {
        scheduleSetup {
            canSendEvent.clear()
        }
    }
    private val roomServiceMock = RoomServiceMock().apply {
        rooms.value = mapOf(room to MutableStateFlow(Room(room)))
    }

    private val roomStore = getInMemoryRoomStore().apply {
        scheduleSetup { update(room) { simpleRoom } }
    }
    private val roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)

    private val cut = OutboxMessageEventHandler(
        MatrixClientConfiguration().apply {
            deleteSentOutboxMessageDelay = 10.seconds
        },
        api,
        roomStore,
        listOf(roomEventDecryptionServiceMock),
        userService,
        mediaServiceMock,
        roomOutboxMessageStore,
        OutboxMessageMediaUploaderMappings.default,
        CurrentSyncState(currentSyncState),
        UserInfo(
            UserId("user", "server"),
            "device",
            Key.Ed25519Key(null, ""),
            Key.Curve25519Key(null, "")
        ),
        TransactionManagerMock(),
        testScope.testClock,
    )

    @Test
    fun `removeOldOutboxMessages » remove old outbox messages`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("")
        val outbox1 = RoomOutboxMessage(room, "transaction1", content, testClock.now())
        val outbox2 =
            RoomOutboxMessage(
                room,
                "transaction2",
                content,
                testClock.now(),
                testClock.now() - 11.seconds
            )
        val outbox3 =
            RoomOutboxMessage(room, "transaction3", content, testClock.now(), testClock.now())

        with(roomOutboxMessageStore) {
            update(outbox1.roomId, outbox1.transactionId) { outbox1 }
            update(outbox2.roomId, outbox2.transactionId) { outbox2 }
            update(outbox3.roomId, outbox3.transactionId) { outbox3 }
        }

        delay(1.seconds)
        cut.removeOldOutboxMessages()
        roomOutboxMessageStore.getAll().flattenValues().first() shouldContainExactly listOf(
            outbox1,
            outbox3
        )
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
            delay(1.seconds)
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 2
            outboxMessages[0].sentAt shouldNotBe null
            outboxMessages[1].sentAt shouldNotBe null
            sendMessageEventCalled shouldBe true
        }

    @Test
    fun `processOutboxMessages » encrypt events in encrypted rooms`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val message =
            RoomOutboxMessage(
                room,
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        val megolmEventContent =
            MegolmEncryptedMessageEventContent(
                MegolmMessageValue("cipher"),
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

        delay(1.seconds)
        val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages shouldHaveSize 1
        outboxMessages.first().sentAt shouldNotBe null

        sendMessageEventCalled shouldBe true
    }

    @Test
    fun `processOutboxMessages » delete messages from unknown room`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val message =
            RoomOutboxMessage(
                RoomId("!unknown"),
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        val megolmEventContent =
            MegolmEncryptedMessageEventContent(
                MegolmMessageValue("cipher"),
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
        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }
        delay(35.seconds)
        roomOutboxMessageStore.getAll().flattenValues().first() shouldHaveSize 0
        sendMessageEventCalled shouldBe false
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
                    mapOf(
                        RoomOutboxMessageRepositoryKey(
                            message.roomId,
                            message.transactionId
                        ) to flowOf(message)
                    ),
                    mapOf(
                        RoomOutboxMessageRepositoryKey(
                            message.roomId,
                            message.transactionId
                        ) to flowOf(message)
                    ),
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
            RoomOutboxMessage(
                room,
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
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

        delay(1.seconds)
        val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages shouldHaveSize 1
        outboxMessages.first().sentAt shouldNotBe null
    }

    @Test
    fun `processOutboxMessages » not retry on MatrixServerException`() = runTest {
        val message =
            RoomOutboxMessage(
                room,
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        var call = 0
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "transaction"),
            ) {
                call++
                when (call) {
                    1 -> throw MatrixServerException(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse.Unknown("")
                    )

                    else -> SendEventResponse(EventId("event"))
                }

            }
        }
        currentSyncState.value = SyncState.RUNNING

        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        delay(1.seconds)
        val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages shouldHaveSize 1
        outboxMessages.first().sentAt shouldBe null
        outboxMessages.first().sendError shouldNotBe null
    }

    @Test
    fun `processOutboxMessages » not send without permissions`() = runTest {
        val message =
            RoomOutboxMessage(
                room,
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
        roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }
        userService.canSendEvent[room to RoomMessageEventContent::class] = flowOf(false)
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(room, "m.room.message", "transaction"),
            ) {
                throw MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse.Unknown("")
                )
            }
        }
        backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

        delay(1.seconds)
        val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages shouldHaveSize 1
        outboxMessages.first().sentAt shouldBe null
        outboxMessages.first().sendError shouldBe RoomOutboxMessage.SendError.NoEventPermission
    }

    @Test
    fun `processOutboxMessages » not retry on ResponseException`() = runTest {
        val message =
            RoomOutboxMessage(
                room,
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
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

        delay(1.seconds)
        val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages shouldHaveSize 1
        outboxMessages.first().sentAt shouldBe null
        outboxMessages.first().sendError shouldNotBe null
    }

    @Test
    fun `processOutboxMessages » retry on MatrixServerException rate limit`() = runTest {
        val message =
            RoomOutboxMessage(
                room,
                "transaction",
                RoomMessageEventContent.TextBased.Text("hi"),
                testClock.now()
            )
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

        val outboxMessages1 = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages1 shouldHaveSize 1
        outboxMessages1.first().sentAt shouldBe null
        delay(1.seconds)
        val outboxMessages2 = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages2 shouldHaveSize 1
        outboxMessages2.first().sentAt shouldBe null
    }

    @Test
    fun `processOutboxMessages » not send message if sending was cancelled during upload`() =
        runTest {
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
            mediaServiceMock.uploadTimer.value = 3.seconds
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

            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                cut.processOutboxMessages(
                    roomOutboxMessageStore.getAll()
                )
            }

            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                delay(100.milliseconds)
                roomOutboxMessageStore.update(message1.roomId, message1.transactionId) { null }
            }
            currentSyncState.value = SyncState.RUNNING
            mediaServiceMock.uploadMediaCalled.first { it == cacheUrl }

            delay(1.seconds)
            val outboxMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages[0].sentAt shouldNotBe null
        }

    @Test
    fun `processOutboxMessages » monitor upload on a file with thumbnail correctly`() = runTest {
        val thumbnailInfo = ThumbnailInfo(15, 15, "image/png", 10)
        mediaServiceMock.uploadTimer.value = 1.seconds
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
        delay(100.milliseconds)
        val outboxMessages1 = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages1 shouldHaveSize 1
        outboxMessages1[0].mediaUploadProgress.value shouldBe FileTransferProgress(0, 35)

        delay(1.seconds)
        val outboxMessages2 = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages2 shouldHaveSize 1
        outboxMessages2[0].mediaUploadProgress.value shouldBe FileTransferProgress(10, 35)

        delay(2.seconds)
        val outboxMessages3 = roomOutboxMessageStore.getAll().flattenValues().first()
        outboxMessages3 shouldHaveSize 1
        outboxMessages3[0].mediaUploadProgress.value shouldBe FileTransferProgress(35, 35)

        delay(1.seconds)
        sendEventCalled shouldBe true
    }

    @Test
    fun `processOutboxMessages » draft message is not send  » set to non draft  » message is send `() =
        runTest {
            val message =
                RoomOutboxMessage(
                    room,
                    "transaction",
                    RoomMessageEventContent.TextBased.Text("hi"),
                    testClock.now(),
                    isDraft = true
                )
            roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }

            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    it shouldBe RoomMessageEventContent.TextBased.Text("hi")
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }

            backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            delay(500)

            sendMessageEventCalled shouldBe false

            roomOutboxMessageStore.update(
                message.roomId,
                message.transactionId
            ) { message.copy(isDraft = false) }

            delay(1.seconds)

            sendMessageEventCalled shouldBe true

            val outboxMessages =
                roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sentAt shouldNotBe null
        }

    @Test
    fun `processOutboxMessages » message with error is not send » message with now removed error is send`() =
        runTest {
            val message =
                RoomOutboxMessage(
                    room,
                    "transaction",
                    RoomMessageEventContent.TextBased.Text("hi"),
                    testClock.now(),
                    sendError = RoomOutboxMessage.SendError.Unknown()
                )
            roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }

            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    it shouldBe RoomMessageEventContent.TextBased.Text("hi")
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }

            backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            delay(500)

            sendMessageEventCalled shouldBe false

            roomOutboxMessageStore.update(
                message.roomId,
                message.transactionId
            ) { message.copy(sendError = null) }

            delay(1.seconds)

            sendMessageEventCalled shouldBe true

            val outboxMessages =
                roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sentAt shouldNotBe null
        }

    @Test
    fun `processOutboxMessages » message fails to send » messages sendError is set to null » message will retry send`() =
        runTest {
            val message =
                RoomOutboxMessage(
                    room,
                    "transaction",
                    RoomMessageEventContent.TextBased.Text("hi"),
                    testClock.now()
                )
            roomOutboxMessageStore.update(message.roomId, message.transactionId) { message }

            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendMessageEvent(room, "m.room.message", "transaction"),
                ) {
                    it shouldBe RoomMessageEventContent.TextBased.Text("hi")
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }

            userService.canSendEvent[room to RoomMessageEventContent::class] = flowOf(false)

            backgroundScope.launch { cut.processOutboxMessages(roomOutboxMessageStore.getAll()) }

            delay(500)

            sendMessageEventCalled shouldBe false
            val outboxMessages =
                roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages shouldHaveSize 1
            outboxMessages.first().sendError shouldNotBe null

            userService.canSendEvent[room to RoomMessageEventContent::class] = flowOf(true)

            roomOutboxMessageStore.update(
                message.roomId,
                message.transactionId
            ) { message.copy(sendError = null) }

            delay(1.seconds)

            sendMessageEventCalled shouldBe true

            val outboxMessages2 =
                roomOutboxMessageStore.getAll().flattenValues().first()
            outboxMessages2 shouldHaveSize 1
            outboxMessages2.first().sentAt shouldNotBe null
        }

}