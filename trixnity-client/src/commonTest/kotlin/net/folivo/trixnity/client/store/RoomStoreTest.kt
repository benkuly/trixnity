package net.folivo.trixnity.client.store

import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.retry
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.InMemoryRoomRepository
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
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
        val room1 = Room(RoomId("room1", "server"))
        val room2 = Room(RoomId("room2", "server"))

        roomRepository.save(room1.roomId, room1)
        roomRepository.save(room2.roomId, room2)

        retry(10, 2_000.milliseconds, 30.milliseconds) {
            cut.getAll().flattenValues().first() shouldContainExactly listOf(room1, room2)
        }
    }
}