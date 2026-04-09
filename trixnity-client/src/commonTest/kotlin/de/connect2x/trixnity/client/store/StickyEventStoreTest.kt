package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.mocks.RepositoryTransactionManagerMock
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryStickyEventRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepositoryFirstKey
import de.connect2x.trixnity.client.store.repository.StickyEventRepositorySecondKey
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.StickyEventData
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(MSC4354::class, MSC4143::class)
class StickyEventStoreTest : TrixnityBaseTest() {
    private val repo = InMemoryStickyEventRepository()
    private val cut = StickyEventStore(
        stickyEventRepository = repo,
        tm = RepositoryTransactionManagerMock(),
        contentMappings = EventContentSerializerMappings.default,
        config = MatrixClientConfiguration(),
        statisticCollector = ObservableCacheStatisticCollector(),
        storeScope = testScope.backgroundScope,
        clock = testScope.testClock,
    )

    private val roomId = RoomId("!room:server")
    private val eventId = EventId("\$event")
    private val sender = UserId("alice", "server")
    private val firstKey = StickyEventRepositoryFirstKey(roomId, "org.matrix.msc4143.rtc.member")
    private val secondKey = StickyEventRepositorySecondKey(sender, "sticky_key")
    private val event = RoomEvent.MessageEvent(
        content = RtcMemberEventContent(stickyKey = "sticky_key", slotId = "1") as StickyEventContent,
        id = eventId,
        sender = sender,
        roomId = roomId,
        originTimestamp = 2000L,
        sticky = StickyEventData(durationMs = 1000L)
    )
    private val storedStickyEvent = StoredStickyEvent(
        event = event,
        startTime = Instant.fromEpochMilliseconds(1),
        endTime = Instant.fromEpochMilliseconds(2),
    )

    @Test
    fun `deleteByEventId - should delete`() = runTest {
        repo.save(firstKey, secondKey, storedStickyEvent)
        cut.deleteByEventId(roomId, eventId)
        repo.get(firstKey, secondKey) shouldBe null
    }

    @Test
    fun `save - should save when endTime after`() = runTest {
        val storedStickyEventWithLaterEndTime = storedStickyEvent.copy(endTime = Instant.fromEpochMilliseconds(3))
        repo.save(firstKey, secondKey, storedStickyEvent)
        cut.save(storedStickyEventWithLaterEndTime)
        repo.get(firstKey, secondKey) shouldBe storedStickyEventWithLaterEndTime
    }

    @Test
    fun `save - should skip when endTime before`() = runTest {
        val storedStickyEventWithEarlierEndTime = storedStickyEvent.copy(endTime = Instant.fromEpochMilliseconds(1))
        repo.save(firstKey, secondKey, storedStickyEvent)
        cut.save(storedStickyEventWithEarlierEndTime)
        repo.get(firstKey, secondKey) shouldBe storedStickyEvent
    }

    @Test
    fun `save - same endTime - should save when lexicographical higher`() = runTest {
        val storedStickyEventWithHigherLexi = storedStickyEvent.copy(event = event.copy(id = EventId("\$zzzz")))
        repo.save(firstKey, secondKey, storedStickyEvent)
        cut.save(storedStickyEventWithHigherLexi)
        repo.get(firstKey, secondKey) shouldBe storedStickyEventWithHigherLexi
    }

    @Test
    fun `save - same endTime - should skip when lexicographical lower`() = runTest {
        val storedStickyEventWithLowerLexi = storedStickyEvent.copy(event = event.copy(id = EventId("\$aaaa")))
        repo.save(firstKey, secondKey, storedStickyEvent)
        cut.save(storedStickyEventWithLowerLexi)
        repo.get(firstKey, secondKey) shouldBe storedStickyEvent
    }

    @Test
    fun `deleteInvalid - should delete when invalid`() = runTest {
        repo.save(firstKey, secondKey, storedStickyEvent)
        delay(3.milliseconds)
        cut.deleteInvalid()
        repo.get(firstKey, secondKey) shouldBe null
    }

    @Test
    fun `getBySenderAndStickyKey - should return null when not valid`() = runTest {
        repo.save(firstKey, secondKey, storedStickyEvent)
        delay(3.milliseconds)
        cut.getBySenderAndStickyKey<RtcMemberEventContent>(roomId, sender, "sticky_key").first() shouldBe null
    }

    @Test
    fun `getBySenderAndStickyKey - should return null when not valid anymore`() = runTest {
        repo.save(firstKey, secondKey, storedStickyEvent)
        val result = backgroundScope.async {
            cut.getBySenderAndStickyKey<RtcMemberEventContent>(roomId, sender, "sticky_key").take(2).toList()
        }
        delay(3.milliseconds)
        result.await() shouldBe listOf(storedStickyEvent, null)
    }
}
