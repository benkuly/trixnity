package net.folivo.trixnity.client.store

import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.InMemoryRoomRepository
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration.Companion.milliseconds

class RoomStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var roomRepository: RoomRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        roomRepository = InMemoryRoomRepository()
        cut = RoomStore(
            roomRepository,
            RepositoryTransactionManagerMock(),
            MatrixClientConfiguration(),
            ObservableCacheStatisticCollector(),
            storeScope
        )
    }
    afterTest {
        storeScope.cancel()
    }

    context(RoomStore::init.name) {
        should("fill cache with values from repository") {
            val room1 = Room(RoomId("room1", "server"))
            val room2 = Room(RoomId("room2", "server"))

            roomRepository.save(room1.roomId, room1)
            roomRepository.save(room2.roomId, room2)

            retry(10, 2_000.milliseconds, 30.milliseconds) {
                cut.getAll().flattenValues().first() shouldContainExactly listOf(room1, room2)
            }
        }
    }
})