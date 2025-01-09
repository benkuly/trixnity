package net.folivo.trixnity.client.store

import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.InMemoryRoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import kotlin.time.Duration.Companion.milliseconds

class RoomOutboxMessageStoreTest : ShouldSpec({
    timeout = 60_000

    lateinit var roomOutboxMessageRepository: RoomOutboxMessageRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomOutboxMessageStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        roomOutboxMessageRepository = InMemoryRoomOutboxMessageRepository()
        cut = RoomOutboxMessageStore(
            roomOutboxMessageRepository,
            RepositoryTransactionManagerMock(),
            MatrixClientConfiguration().apply {
                cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(50.milliseconds)
            },
            ObservableCacheStatisticCollector(),
            storeScope,
            Clock.System,
        )
    }
    afterTest {
        storeScope.cancel()
    }

    val room = RoomId("room", "server")

    context(RoomOutboxMessageStore::init.name) {
        should("fill cache with values from repository") {
            val message1 = RoomOutboxMessage(room, "t1", Text(""), Clock.System.now())
            val message2 = RoomOutboxMessage(room, "t2", Text(""), Clock.System.now())
            roomOutboxMessageRepository.save(RoomOutboxMessageRepositoryKey(room, "t1"), message1)
            roomOutboxMessageRepository.save(RoomOutboxMessageRepositoryKey(room, "t2"), message2)

            retry(10, 2_000.milliseconds, 30.milliseconds) {
                cut.getAll().flattenValues().first() shouldContainExactly listOf(message1, message2)
            }
        }
    }
    should("handle massive save and delete") {
        val job1 = launch {
            cut.getAll().flattenValues().collect { outbox ->
                outbox.forEach { cut.update(it.roomId, it.transactionId) { it?.copy(sentAt = Clock.System.now()) } }
                delay(10)
            }
        }
        val job2 = launch {
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
        job1.cancel()
        job2.cancel()
    }
})