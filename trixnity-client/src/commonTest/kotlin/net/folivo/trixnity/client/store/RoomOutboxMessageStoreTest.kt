package net.folivo.trixnity.client.store

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.retry
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.InMemoryRoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class RoomOutboxMessageStoreTest : TrixnityBaseTest() {

    private val room = RoomId("room", "server")

    private val roomOutboxMessageRepository = InMemoryRoomOutboxMessageRepository() as RoomOutboxMessageRepository
    private val cut = RoomOutboxMessageStore(
        roomOutboxMessageRepository,
        RepositoryTransactionManagerMock(),
        MatrixClientConfiguration().apply {
            cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(50.milliseconds)
        },
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    )

    @Test
    fun `init » fill cache with values from repository`() = runTest {
        val message1 = RoomOutboxMessage(room, "t1", Text(""), Clock.System.now())
        val message2 = RoomOutboxMessage(room, "t2", Text(""), Clock.System.now())
        roomOutboxMessageRepository.save(RoomOutboxMessageRepositoryKey(room, "t1"), message1)
        roomOutboxMessageRepository.save(RoomOutboxMessageRepositoryKey(room, "t2"), message2)

        retry(10, 2_000.milliseconds, 30.milliseconds) {
            cut.getAll().flattenValues().first() shouldContainExactly listOf(message1, message2)
        }
    }

    @Test
    fun `handle massive save and delete`() = runTest {
        backgroundScope.launch {
            cut.getAll().flattenValues().collect { outbox ->
                outbox.forEach { cut.update(it.roomId, it.transactionId) { it?.copy(sentAt = Clock.System.now()) } }
                delay(10)
            }
        }
        backgroundScope.launch {
            cut.getAll().flattenValues().collect { outbox ->
                outbox.forEach { cut.update(it.roomId, it.transactionId) { null } }
                delay(10)
            }
        }
        repeat(50) { i ->
            cut.update(room, i.toString()) {
                RoomOutboxMessage(room, i.toString(), Text(""), Clock.System.now())
            }
        }
        cut.getAll().flatten().first { it.isEmpty() } // we get a timeout if this never succeeds
    }
}