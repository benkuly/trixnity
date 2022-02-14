package net.folivo.trixnity.client.store

import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration.Companion.milliseconds

class RoomOutboxMessageStoreTest : ShouldSpec({
    timeout = 60_000

    val roomOutboxMessageRepository = mockk<RoomOutboxMessageRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomOutboxMessageStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = RoomOutboxMessageStore(roomOutboxMessageRepository, NoopRepositoryTransactionManager, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(RoomOutboxMessageStore::init.name) {
        should("fill cache with values from repository") {
            val message1 = mockk<RoomOutboxMessage<*>> {
                coEvery { transactionId } returns "t1"
            }
            val message2 = mockk<RoomOutboxMessage<*>> {
                coEvery { transactionId } returns "t2"
            }
            coEvery { roomOutboxMessageRepository.getAll() }.returns(listOf(message1, message2))

            cut.init()

            retry(10, 2_000.milliseconds, 30.milliseconds) {
                cut.getAll().value shouldContainExactly listOf(message1, message2)
            }
        }
    }
    should("handle massive save and delete") {
        val job1 = launch {
            cut.getAll().collect { outbox ->
                outbox.forEach { cut.update(it.transactionId) { it?.copy(sentAt = Clock.System.now()) } }
                delay(10)
            }
        }
        val job2 = launch {
            cut.getAll().collect { outbox ->
                outbox.forEach { cut.update(it.transactionId) { null } }
                delay(10)
            }
        }
        repeat(50) { i ->
            cut.update(i.toString()) { RoomOutboxMessage(i.toString(), RoomId("room", "server"), mockk()) }
        }
        cut.getAll().first { it.isEmpty() } // we get a timeout if this never succeeds
        job1.cancel()
        job2.cancel()
    }
})