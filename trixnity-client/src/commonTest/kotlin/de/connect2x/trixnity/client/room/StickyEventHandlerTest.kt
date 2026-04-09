package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryStickyEventStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.StickyEventData
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(MSC4354::class, MSC4143::class)
class StickyEventHandlerTest : TrixnityBaseTest() {
    private val roomId = RoomId("!room:server")
    private val alice = UserId("alice", "server")

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)
    private val store = getInMemoryStickyEventStore()
    private val encryptionService = RoomEventEncryptionServiceMock().apply {
        scheduleSetup {
            returnDecrypt = null
            decryptCounter = 0
        }
    }

    private val cut = StickyEventHandler(
        api = api,
        stickyEventStore = store,
        roomEventEncryptionServices = listOf(encryptionService),
        clock = testScope.testClock,
        tm = TransactionManagerMock(),
        config = MatrixClientConfiguration().apply { experimentalFeatures.enableMSC4354 = true },
    )

    @Test
    fun `setStickyEvents - skip when not sticky`() = runTest {
        val event = RoomEvent.MessageEvent(
            content = RtcMemberEventContent(stickyKey = "sticky", slotId = "1") as StickyEventContent,
            id = EventId("\$event"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 1000L,
            sticky = null
        )
        cut.setStickyEvents(listOf(event))
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky").first() shouldBe null
    }

    @Test
    fun `setStickyEvents - set start time based on min`() = runTest {
        // originTimestamp < now
        val event1 = RoomEvent.MessageEvent(
            content = RtcMemberEventContent(stickyKey = "sticky1", slotId = "1") as StickyEventContent,
            id = EventId("\$event1"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 1000L,
            sticky = StickyEventData(durationMs = 1000L)
        )
        // originTimestamp > now
        val event2 = RoomEvent.MessageEvent(
            content = RtcMemberEventContent(stickyKey = "sticky2", slotId = "2") as StickyEventContent,
            id = EventId("\$event2"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 3000L,
            sticky = StickyEventData(durationMs = 1000L)
        )
        delay(2.seconds)
        cut.setStickyEvents(listOf(event1, event2))

        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky1")
            .first()?.startTime shouldBe Instant.fromEpochMilliseconds(0) + 1.seconds
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky2")
            .first()?.startTime shouldBe Instant.fromEpochMilliseconds(0) + 2.seconds
    }

    @Test
    fun `setStickyEvents - set send time based on start time and bounds`() = runTest {
        // negative duration
        val event1 = RoomEvent.MessageEvent(
            content = RtcMemberEventContent(stickyKey = "sticky1", slotId = "1") as StickyEventContent,
            id = EventId("\$event1"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 2000L,
            sticky = StickyEventData(durationMs = -1L)
        )
        // normal duration
        val event2 = RoomEvent.MessageEvent(
            content = RtcMemberEventContent(stickyKey = "sticky2", slotId = "2") as StickyEventContent,
            id = EventId("\$event2"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 2000L,
            sticky = StickyEventData(durationMs = 10.minutes.inWholeMilliseconds)
        )
        // over 1 hour
        val event3 = RoomEvent.MessageEvent(
            content = RtcMemberEventContent(stickyKey = "sticky3", slotId = "3") as StickyEventContent,
            id = EventId("\$event3"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 2000L,
            sticky = StickyEventData(durationMs = 2.hours.inWholeMilliseconds)
        )
        delay(2.seconds)
        cut.setStickyEvents(listOf(event1, event2, event3))

        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky1").first()
            ?.endTime shouldBe Instant.fromEpochMilliseconds(0) + 2.seconds
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky2").first()
            ?.endTime shouldBe Instant.fromEpochMilliseconds(0) + 2.seconds + 10.minutes
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky3").first()
            ?.endTime shouldBe Instant.fromEpochMilliseconds(0) + 2.seconds + 1.hours
    }

    @Test
    fun `setEncryptedStickyEvents - skip when not sticky`() = runTest {
        val eventContent = MegolmEncryptedMessageEventContent(
            ciphertext = MegolmMessageValue("cipher"),
            senderKey = Curve25519KeyValue("senderKey"),
            deviceId = "deviceId",
            sessionId = "sessionId"
        )
        val event: RoomEvent.MessageEvent<EncryptedMessageEventContent> = RoomEvent.MessageEvent(
            content = eventContent,
            id = EventId("\$event"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 1000L,
            sticky = null
        )
        cut.setEncryptedStickyEvents(listOf(event))
        encryptionService.decryptCounter shouldBe 0
    }

    @Test
    fun `setEncryptedStickyEvents - decrypt`() = runTest {
        val eventContent = MegolmEncryptedMessageEventContent(
            ciphertext = MegolmMessageValue("cipher"),
            senderKey = Curve25519KeyValue("senderKey"),
            deviceId = "deviceId",
            sessionId = "sessionId"
        )
        val encryptedEvent: RoomEvent.MessageEvent<EncryptedMessageEventContent> = RoomEvent.MessageEvent(
            content = eventContent,
            id = EventId("\$event"),
            sender = alice,
            roomId = roomId,
            originTimestamp = 1000L,
            sticky = StickyEventData(durationMs = 1000L)
        )
        val decryptedContent = RtcMemberEventContent(stickyKey = "sticky", slotId = "1")
        encryptionService.returnDecrypt = Result.success(decryptedContent)

        cut.setEncryptedStickyEvents(listOf(encryptedEvent))

        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky").first().shouldNotBeNull()
            .event.content shouldBe decryptedContent
    }

    @Test
    fun `removeInvalidStickyEvents - periodically`() = runTest {
        store.save(
            StoredStickyEvent(
                event = RoomEvent.MessageEvent(
                    content = RtcMemberEventContent(stickyKey = "sticky1", slotId = "1") as StickyEventContent,
                    id = EventId("\$event"),
                    sender = alice,
                    roomId = roomId,
                    originTimestamp = 2000L,
                    sticky = StickyEventData(durationMs = 1000L)
                ),
                startTime = Instant.fromEpochMilliseconds(0),
                endTime = Instant.fromEpochMilliseconds(0) + 2.minutes,
            ),
        )
        store.save(
            StoredStickyEvent(
                event = RoomEvent.MessageEvent(
                    content = RtcMemberEventContent(stickyKey = "sticky2", slotId = "1") as StickyEventContent,
                    id = EventId("\$event"),
                    sender = alice,
                    roomId = roomId,
                    originTimestamp = 2000L,
                    sticky = StickyEventData(durationMs = 1000L)
                ),
                startTime = Instant.fromEpochMilliseconds(0),
                endTime = Instant.fromEpochMilliseconds(0) + 4.minutes,
            ),
        )
        val job = backgroundScope.launch { cut.removeInvalidStickyEvents() }
        delay(1.seconds)
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky1").first().shouldNotBeNull()
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky2").first().shouldNotBeNull()

        delay(2.minutes)
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky1").first().shouldBeNull()
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky2").first().shouldNotBeNull()

        delay(2.minutes)
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky1").first().shouldBeNull()
        store.getBySenderAndStickyKey(roomId, RtcMemberEventContent::class, alice, "sticky2").first().shouldBeNull()
        job.cancel()
    }
}
