package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.getInMemoryRoomAccountDataStore
import de.connect2x.trixnity.client.getInMemoryRoomOutboxMessageStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.getInMemoryRoomTimelineStore
import de.connect2x.trixnity.client.getInMemoryStickyEventStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.MediaServiceMock
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.TimelineEventHandlerMock
import de.connect2x.trixnity.client.retry
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.client.simpleUserInfo
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.TimelineEvent.TimelineEventContentError
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.SyncState.RUNNING
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.Relations
import de.connect2x.trixnity.core.model.events.m.ServerAggregation
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(MSC4354::class)
class RoomServiceTest : TrixnityBaseTest() {

    private val room = simpleRoom.roomId

    private val roomStore = getInMemoryRoomStore()
    private val roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore()
    private val roomTimelineStore = getInMemoryRoomTimelineStore()

    private val mediaServiceMock = MediaServiceMock()
    private val roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock()

    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    private val api = mockMatrixClientServerApiClient()

    private val cut = RoomServiceImpl(
        api = api,
        roomStore = roomStore,
        roomStateStore = getInMemoryRoomStateStore(),
        roomAccountDataStore = getInMemoryRoomAccountDataStore(),
        roomTimelineStore = roomTimelineStore,
        stickyEventStore = getInMemoryStickyEventStore(),
        roomOutboxMessageStore = roomOutboxMessageStore,
        roomEventEncryptionServices = listOf(roomEventDecryptionServiceMock),
        mediaService = mediaServiceMock,
        forgetRoomService = { _, _ -> },
        userInfo = simpleUserInfo,
        timelineEventHandler = TimelineEventHandlerMock(),
        clock = testScope.testClock,
        matrixClientConfig = MatrixClientConfiguration(),
        typingEventHandler = TypingEventHandlerImpl(api),
        currentSyncState = CurrentSyncState(currentSyncState),
        scope = testScope.backgroundScope
    )

    private val eventId = EventId("\$event1")
    private val session = "SESSION"
    private val senderKey = Key.Curve25519Key(null, "senderKey")
    private val encryptedEventContent = MegolmEncryptedMessageEventContent(
        MegolmMessageValue("ciphertext"), senderKey.value, "SENDER", session
    )
    private val encryptedTimelineEvent = TimelineEvent(
        event = MessageEvent(
            encryptedEventContent,
            eventId,
            UserId("sender", "server"),
            room,
            1
        ),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )
    private val replaceTimelineEvent = TimelineEvent(
        event = MessageEvent(
            encryptedEventContent, // in reality there is a relatesTo
            EventId("\$event2"),
            UserId("sender", "server"),
            room,
            1
        ),
        content = Result.success(
            RoomMessageEventContent.TextBased.Text(
                "*edited hi",
                relatesTo = RelatesTo.Replace(
                    EventId("\$event1"),
                    RoomMessageEventContent.TextBased.Text("edited hi")
                )
            )
        ),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )
    private val timelineEvent = TimelineEvent(
        event = MessageEvent(
            encryptedEventContent,
            EventId("\$event1"),
            UserId("sender", "server"),
            room,
            1,
            UnsignedRoomEventData.UnsignedMessageEventData(
                relations = Relations(
                    mapOf(
                        RelationType.Replace to ServerAggregation.Replace(
                            replaceTimelineEvent.eventId,
                            replaceTimelineEvent.event.sender,
                            replaceTimelineEvent.event.originTimestamp
                        )
                    )
                )
            )
        ),
        content = Result.success(RoomMessageEventContent.TextBased.Text("hi")),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )


    @Test
    fun `getTimelineEvent » event not in database » try fill gaps until found`() = runTest {
        val lastEventId = EventId("\$eventWorld")
        roomStore.update(room) { simpleRoom.copy(lastEventId = lastEventId) }
        currentSyncState.value = RUNNING
        val event = MessageEvent(
            RoomMessageEventContent.TextBased.Text("hello"),
            eventId,
            UserId("sender", "server"),
            room,
            1
        )
        roomTimelineStore.addAll(
            listOf(
                TimelineEvent(
                    event = MessageEvent(
                        RoomMessageEventContent.TextBased.Text("world"),
                        lastEventId,
                        UserId("sender", "server"),
                        room,
                        0
                    ),
                    previousEventId = null,
                    nextEventId = null,
                    gap = TimelineEvent.Gap.GapBefore("start")
                )
            )
        )
        val timelineEventFlow = cut.getTimelineEvent(room, eventId)
        roomTimelineStore.addAll(
            listOf(
                TimelineEvent(
                    event = event,
                    previousEventId = null,
                    nextEventId = lastEventId,
                    gap = TimelineEvent.Gap.GapBefore("end")
                )
            )
        )
        timelineEventFlow.filterNotNull().first() shouldBe
                TimelineEvent(
                    event = event,
                    previousEventId = null,
                    nextEventId = lastEventId,
                    gap = TimelineEvent.Gap.GapBefore("end")
                )
    }

    private fun shouldJustReturnEvent(
        timelineEvent: TimelineEvent
    ) = runTest {
        roomTimelineStore.addAll(listOf(timelineEvent))
        cut.getTimelineEvent(room, eventId).first() shouldBe timelineEvent

        // event gets changed later (e.g. redaction)
        roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
        val result = cut.getTimelineEvent(room, eventId)
        delay(20)
        roomTimelineStore.addAll(listOf(timelineEvent))
        delay(20)
        result.first() shouldBe timelineEvent
    }

    @Test
    fun `getTimelineEvent » should just return event » with already encrypted event`() =
        shouldJustReturnEvent(
            encryptedTimelineEvent.copy(
                content = Result.success(RoomMessageEventContent.TextBased.Text("hi"))
            )
        )

    @Test
    fun `getTimelineEvent » should just return event » with encryption error`() =
        shouldJustReturnEvent(
            encryptedTimelineEvent.copy(
                content = Result.failure(TimelineEventContentError.DecryptionTimeout)
            )
        )

    @Test
    fun `getTimelineEvent » should just return event » without RoomEvent`() = shouldJustReturnEvent(
        encryptedTimelineEvent.copy(
            event = nameEvent(1)
        )
    )

    @Test
    fun `getTimelineEvent » should just return event » without MegolmEncryptedEventContent`() =
        shouldJustReturnEvent(
            encryptedTimelineEvent.copy(
                event = textEvent(1)
            )
        )

    @Test
    fun `getTimelineEvent » event can be decrypted » decrypt event`() = runTest {
        val expectedDecryptedEvent = RoomMessageEventContent.TextBased.Text("decrypted")
        roomEventDecryptionServiceMock.returnDecrypt = Result.success(expectedDecryptedEvent)
        roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
        val result = cut.getTimelineEvent(room, eventId)
            .first { it?.content?.getOrNull() != null }
        assertSoftly(result) {
            assertNotNull(this)
            event shouldBe encryptedTimelineEvent.event
            content?.getOrNull() shouldBe expectedDecryptedEvent
        }
    }

    @Test
    fun `getTimelineEvent » event can be decrypted » decrypt event only once`() = runTest {
        val expectedDecryptedEvent = RoomMessageEventContent.TextBased.Text("decrypted")
        roomEventDecryptionServiceMock.returnDecrypt = Result.success(expectedDecryptedEvent)
        roomTimelineStore.addAll(listOf(encryptedTimelineEvent))

        repeat(100) {
            cut.getTimelineEvent(room, eventId)
                .first { it?.content?.getOrNull() != null }
        }
        roomEventDecryptionServiceMock.decryptCounter shouldBe 1
    }

    @Test
    fun `getTimelineEvent » event can be decrypted » timeout when decryption takes too long`() =
        runTest {
            roomEventDecryptionServiceMock.decryptDelay = 10.seconds
            roomEventDecryptionServiceMock.returnDecrypt = null
            roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
            val result =
                async { cut.getTimelineEvent(room, eventId) { decryptionTimeout = ZERO }.collect() }
            result.job.children.count() shouldBe 0 // there are no decryption jobs
            result.cancel()
        }

    @Test
    fun `getTimelineEvent » event can be decrypted » retry on decryption timeout`() = runTest {
        roomEventDecryptionServiceMock.decryptDelay = 10.seconds
        roomEventDecryptionServiceMock.returnDecrypt = null
        roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
        withTimeoutOrNull(100.milliseconds) {
            cut.getTimelineEvent(room, eventId) { decryptionTimeout = 50.milliseconds }.collect()
        }
        withTimeoutOrNull(100.milliseconds) {
            cut.getTimelineEvent(room, eventId) { decryptionTimeout = 50.milliseconds }.collect()
        }
        roomEventDecryptionServiceMock.decryptCounter shouldBe 2
    }

    @Test
    fun `getTimelineEvent » event can be decrypted » handle error`() = runTest {
        roomEventDecryptionServiceMock.returnDecrypt =
            Result.failure(OlmEncryptionService.DecryptMegolmError.MegolmKeyUnknownMessageIndex())
        roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
        val result = cut.getTimelineEvent(room, eventId)
            .first { it?.content?.isFailure == true }
        assertSoftly(result) {
            assertNotNull(this)
            event shouldBe encryptedTimelineEvent.event
            content?.exceptionOrNull() shouldBe
                    TimelineEventContentError.DecryptionError(OlmEncryptionService.DecryptMegolmError.MegolmKeyUnknownMessageIndex())
        }
    }

    @Test
    fun `getTimelineEvent » content has been replaced » replace content with content of other timeline event`() =
        runTest {
            roomTimelineStore.addAll(listOf(timelineEvent, replaceTimelineEvent))
            cut.getTimelineEvent(room, eventId).first() shouldBe timelineEvent.copy(
                content = Result.success(RoomMessageEventContent.TextBased.Text("edited hi"))
            )
        }

    @Test
    fun `getTimelineEvent » content has been replaced » keep original relation`() =
        runTest {
            val timelineEventWithRelation =
                TimelineEvent(
                    event = MessageEvent(
                        encryptedEventContent.copy(
                            relatesTo = RelatesTo.Reply(
                                RelatesTo.ReplyTo(
                                    EventId("\$replyTo")
                                )
                            )
                        ),
                        EventId("\$event1"),
                        UserId("sender", "server"),
                        room,
                        1,
                        UnsignedRoomEventData.UnsignedMessageEventData(
                            relations = Relations(
                                mapOf(
                                    RelationType.Replace to ServerAggregation.Replace(
                                        replaceTimelineEvent.eventId,
                                        replaceTimelineEvent.event.sender,
                                        replaceTimelineEvent.event.originTimestamp
                                    )
                                )
                            )
                        )
                    ),
                    content = Result.success(RoomMessageEventContent.TextBased.Text("hi")),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null
                )
            roomTimelineStore.addAll(listOf(timelineEventWithRelation, replaceTimelineEvent))
            cut.getTimelineEvent(room, eventId).first() shouldBe timelineEventWithRelation.copy(
                content = Result.success(
                    RoomMessageEventContent.TextBased.Text(
                        "edited hi",
                        relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(EventId("\$replyTo")))
                    )
                )
            )
        }

    @Test
    fun `getTimelineEvent » content has been replaced » not replace content when disabled`() =
        runTest {
            roomTimelineStore.addAll(listOf(timelineEvent, replaceTimelineEvent))
            cut.getTimelineEvent(room, eventId) { allowReplaceContent = false }
                .first() shouldBe timelineEvent.copy(
                content = Result.success(RoomMessageEventContent.TextBased.Text("hi"))
            )
        }

    @Test
    fun `getTimelineEvent » content has been replaced » not replace when redacted`() = runTest {
        val redactedTimelineEvent = TimelineEvent(
            event = MessageEvent(
                RedactedEventContent("m.room.message"),
                EventId("\$event1"),
                UserId("sender", "server"),
                room,
                1,
                UnsignedRoomEventData.UnsignedMessageEventData(
                    relations = Relations(
                        mapOf(
                            RelationType.Replace to ServerAggregation.Replace(
                                replaceTimelineEvent.eventId,
                                replaceTimelineEvent.event.sender,
                                replaceTimelineEvent.event.originTimestamp
                            )
                        )
                    )
                )
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        roomTimelineStore.addAll(listOf(redactedTimelineEvent, replaceTimelineEvent))
        cut.getTimelineEvent(room, eventId).first() shouldBe redactedTimelineEvent
    }

    @Test
    fun `getLastTimelineEvent » return last event of room`() = runTest {
        val initialRoom = Room(room, lastEventId = null)
        val event1 = textEvent(1)
        val event2 = textEvent(2)
        val event2Timeline = TimelineEvent(
            event = event2,
            content = null,
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        roomTimelineStore.addAll(listOf(event2Timeline))
        val result = async {
            cut.getLastTimelineEvent(room).take(3).toList()
        }

        with(roomStore) {
            delay(50)
            update(room) { initialRoom }
            delay(50)
            update(room) { initialRoom.copy(lastEventId = event1.id) }
            delay(50)
            update(room) { initialRoom.copy(lastEventId = event2.id) }
        }

        result.await()[0] shouldBe null
        withTimeoutOrNull(100.milliseconds) {
            result.await()[1].shouldNotBeNull().first()
        } shouldBe null
        result.await()[2].shouldNotBeNull().first() shouldBe event2Timeline
    }

    @Test
    fun `sendMessage » just save message in store for later use`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        cut.sendMessage(room) {
            contentBuilder = { content }
        }
        retry(
            100,
            3_000.milliseconds,
            30.milliseconds
        ) {// we need this, because the cache may not be fast enough
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content
                transactionId.length shouldBeGreaterThan 12
            }
        }
    }

    @Test
    fun `setDraftMessage » draft Message being created`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        cut.setDraftMessage(room) {
            contentBuilder = { content }
        }
        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content
                isDraft shouldBe true
            }
        }
    }

    @Test
    fun `setDraftMessage » sendDraftMessage » draft Message set to non draft`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        cut.setDraftMessage(room) {
            contentBuilder = { content }
        }

        cut.sendDraftMessage(room)

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content
                isDraft shouldBe false
            }
        }
    }

    @Test
    fun `setDraftMessage » getDraftMessaage returns draft Message`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        cut.setDraftMessage(room) {
            contentBuilder = { content }
        }

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val draftMessage = cut.getDraftMessage(room).first()
            draftMessage.shouldNotBeNull()
            assertSoftly(draftMessage) {
                roomId shouldBe room
                content shouldBe content
                isDraft shouldBe true
            }
        }
    }

    @Test
    fun `setDraftMessage » setDraftMessage » updates draft message`() = runTest {
        val content1 = RoomMessageEventContent.TextBased.Text("hi")
        cut.setDraftMessage(room) {
            contentBuilder = { content1 }
        }
        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content1
                isDraft shouldBe true
            }
        }

        val content2 = RoomMessageEventContent.TextBased.Text("hi2")
        cut.setDraftMessage(room) {
            contentBuilder = { content2 }
        }
        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content2
                isDraft shouldBe true
            }
        }
    }

    @Test
    fun `getOutbox filters out draft Messages`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        cut.setDraftMessage(room) {
            contentBuilder = { content }
        }

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboxMessages = cut.getOutbox(room).first()
            outboxMessages shouldHaveSize 0
            val allOutboxMessages = cut.getOutbox().first()
            allOutboxMessages shouldHaveSize 0
        }
    }

    @Test
    fun `getOutbox of roomId filters out events from other rooms`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        cut.sendMessage(room) {
            contentBuilder = { content }
        }

        val room2 = RoomId("123")

        val content2 = RoomMessageEventContent.TextBased.Text("hi2")
        cut.sendMessage(room2) {
            contentBuilder = { content2 }
        }

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboxMessages = cut.getOutbox(room).first()
            outboxMessages shouldHaveSize 1
            outboxMessages[0].first()?.content shouldBe content
        }
    }

    @Test
    fun `deleteDraftMessage » all draft events are deleted`() = runTest {
        val content1 = RoomMessageEventContent.TextBased.Text("hi")
        val message1 = RoomOutboxMessage<RoomMessageEventContent.TextBased.Text>(
            roomId = room,
            transactionId = "1",
            content = content1,
            createdAt = Clock.System.now(),
            sentAt = null,
            eventId = null,
            sendError = null,
            keepMediaInCache = true,
            isDraft = true,
        )
        val message2 = RoomOutboxMessage<RoomMessageEventContent.TextBased.Text>(
            roomId = room,
            transactionId = "2",
            content = content1,
            createdAt = Clock.System.now(),
            sentAt = null,
            eventId = null,
            sendError = null,
            keepMediaInCache = true,
            isDraft = true,
        )

        roomOutboxMessageStore.update(room, "1") {
            message1
        }
        roomOutboxMessageStore.update(room, "2") {
            message2
        }

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 2
        }

        cut.deleteDraftMessage(room)

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 0
        }
    }

    @Test
    fun `setDraftMessage twice concurrently will only create one draft message`() = runTest {
        val content = RoomMessageEventContent.TextBased.Text("hi")
        val content2 = RoomMessageEventContent.TextBased.Text("ELECTRICBOOGALOO")
        testScope.launch {
            cut.setDraftMessage(room) {
                contentBuilder = { content }
            }
        }
        testScope.launch {
            cut.setDraftMessage(room) {
                contentBuilder = { content2 }
            }
        }

        retry(100, 3_000.milliseconds, 30.milliseconds) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
        }
    }

    @Test
    fun `retrySendMessage » sendError is set to null`() = runTest {
        val content1 = RoomMessageEventContent.TextBased.Text("hi")
        val message1 = RoomOutboxMessage<RoomMessageEventContent.TextBased.Text>(
            roomId = room,
            transactionId = "1",
            content = content1,
            createdAt = Clock.System.now(),
            sentAt = null,
            eventId = null,
            sendError = RoomOutboxMessage.SendError.NoEventPermission,
            keepMediaInCache = true,
            isDraft = false,
        )

        roomOutboxMessageStore.update(room, "1") {
            message1
        }

        cut.retrySendMessage(room, "1")

        retry(
            100,
            3_000.milliseconds,
            30.milliseconds
        ) {
            val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content
                sendError shouldBe null
            }
        }
    }

    private fun textEvent(i: Long = 24): MessageEvent<RoomMessageEventContent.TextBased.Text> {
        return MessageEvent(
            RoomMessageEventContent.TextBased.Text("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }

    private fun nameEvent(i: Long = 60): StateEvent<NameEventContent> {
        return StateEvent(
            NameEventContent("The room name"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i,
            stateKey = ""
        )
    }
}
