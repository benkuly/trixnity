package de.connect2x.trixnity.client.store

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.mocks.RepositoryTransactionManagerMock
import de.connect2x.trixnity.client.retry
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryRoomRepository
import de.connect2x.trixnity.client.store.repository.RoomRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class RoomStoreTest : TrixnityBaseTest() {

    private val roomRepository = InMemoryRoomRepository() as RoomRepository
    private val cut = RoomStore(
        roomRepository,
        RepositoryTransactionManagerMock(),
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    )

    @Test
    fun `init » fill cache with values from repository`() = runTest {
        val room1 = Room(RoomId("!room1:server"))
        val room2 = Room(RoomId("!room2:server"))

        roomRepository.save(room1.roomId, room1)
        roomRepository.save(room2.roomId, room2)

        retry(10, 2_000.milliseconds, 30.milliseconds) {
            cut.getAll().flattenValues().first() shouldContainExactly listOf(room1, room2)
        }
    }
}