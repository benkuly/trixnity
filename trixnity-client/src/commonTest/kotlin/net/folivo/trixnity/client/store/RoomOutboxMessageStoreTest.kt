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
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoomOutboxMessageStoreTest : ShouldSpec({
    val roomOutboxMessageRepository = mockk<RoomOutboxMessageRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomOutboxMessageStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = RoomOutboxMessageStore(roomOutboxMessageRepository, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(RoomOutboxMessageStore::init.name) {
        should("fill cache with values from repository") {
            val message1 = mockk<RoomOutboxMessage> {
                coEvery { transactionId } returns "t1"
            }
            val message2 = mockk<RoomOutboxMessage> {
                coEvery { transactionId } returns "t2"
            }
            coEvery { roomOutboxMessageRepository.getAll() }.returns(listOf(message1, message2))

            cut.init()

            retry(10, milliseconds(2_000), milliseconds(30)) {
                cut.getAll().value shouldContainExactly listOf(message1, message2)
            }
        }
    }
})