package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RepositoryStateFlowCacheTest : ShouldSpec({
    val repository = mockk<MinimalStoreRepository<String, String>>(relaxUnitFun = true)
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: RepositoryStateFlowCache<String, String, MinimalStoreRepository<String, String>>
    val transactionWasCalled = MutableStateFlow(false)
    val rtm = object : RepositoryTransactionManager {
        override suspend fun <T> transaction(block: suspend () -> T): T {
            transactionWasCalled.value = true
            return block()
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        transactionWasCalled.value = false
    }
    afterTest {
        clearAllMocks()
        cacheScope.cancel()
        cut = mockk() // just in case we forgot to init a new cut for a test
    }

    context("get") {
        beforeTest { cut = RepositoryStateFlowCache(cacheScope, repository, rtm) }
        should("read from database") {
            coEvery { repository.get("key") } returns "value"
            cut.get("key") shouldBe "value"
            transactionWasCalled.value shouldBe true
        }
        should("not use transaction when flag ist set") {
            coEvery { repository.get("key") } returns "value"
            cut.get("key", withTransaction = false) shouldBe "value"
            transactionWasCalled.value shouldBe false
        }
        should("prefer cache") {
            coEvery { repository.get("key") } returns "value" andThen "value2"
            cut.get("key") shouldBe "value"
            cut.get("key") shouldBe "value"
            transactionWasCalled.value shouldBe true
        }
    }
    context("update") {
        beforeTest { cut = RepositoryStateFlowCache(cacheScope, repository, rtm) }
        should("read from database") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
            transactionWasCalled.value shouldBe true
        }
        should("prefer cache") {
            coEvery { repository.get("key") } returns "old" andThen "dino"
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
            cut.update("key") {
                it shouldBe "value"
                "new value"
            }
            transactionWasCalled.value shouldBe true
        }
        should("save to database") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key") { "value" }
            coVerify { repository.save("key", "value") }
            transactionWasCalled.value shouldBe true
        }
        should("allow multiple writes") {
            coEvery { repository.get("key") } returns "old"
            val job1 = launch {
                cut.update("key") {
                    delay(200) // this ensures, that all updates are in here
                    "value1"
                }
            }
            val job2 = launch {
                cut.update("key") {
                    delay(200) // this ensures, that all updates are in here
                    "value2"
                }
            }
            job1.join()
            job2.join()
            coVerify {
                repository.save("key", "value1")
                repository.save("key", "value2")
            }
            transactionWasCalled.value shouldBe true
        }
        should("remove from database") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key") { null }
            coVerify { repository.delete("key") }
            transactionWasCalled.value shouldBe true
        }
        should("not save to repository when flag is set") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key", persistIntoRepository = false) { "value" }
            coVerify(exactly = 0) { repository.save("key", "value") }
        }
        should("not use transaction when flag is set") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key", withTransaction = false) { "value" }
            coVerify { repository.save("key", "value") }
            transactionWasCalled.value shouldBe false
        }
    }
})