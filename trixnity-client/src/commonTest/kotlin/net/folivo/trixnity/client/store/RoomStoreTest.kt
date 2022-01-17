package net.folivo.trixnity.client.store

import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration.Companion.milliseconds

class RoomStoreTest : ShouldSpec({
    val roomRepository = mockk<RoomRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = RoomStore(roomRepository, NoopRepositoryTransactionManager, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(RoomStore::init.name) {
        should("fill cache with values from repository") {
            val room1 = mockk<Room> {
                coEvery { roomId } returns RoomId("room1", "server")
            }
            val room2 = mockk<Room> {
                coEvery { roomId } returns RoomId("room2", "server")
            }
            coEvery { roomRepository.getAll() }.returns(listOf(room1, room2))

            cut.init()

            retry(10, 2_000.milliseconds, 30.milliseconds) {
                cut.getAll().value.values.map { it.value } shouldContainExactly listOf(room1, room2)
            }
        }
    }
})