package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.repository.InMemoryMinimalStoreRepository
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

class RepositoryStateFlowCacheTest : ShouldSpec({
    timeout = 10_000
    lateinit var repository: MinimalStoreRepository<String, String>
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: RepositoryStateFlowCache<String, String, MinimalStoreRepository<String, String>>
    val transactionWasCalled = MutableStateFlow(false)
    val rtm = object : RepositoryTransactionManager {
        override suspend fun <T> readTransaction(block: suspend () -> T): T {
            transactionWasCalled.value = true
            return block()
        }

        override suspend fun <T> writeTransaction(block: suspend () -> T): T {
            transactionWasCalled.value = true
            return block()
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        transactionWasCalled.value = false
        repository = InMemoryMinimalStoreRepository()
        cut = RepositoryStateFlowCache(cacheScope, repository, rtm)
    }
    afterTest {
        cacheScope.cancel()
    }

    context("get") {
        should("read from database") {
            repository.save("key", "value")
            cut.get("key").first() shouldBe "value"
            transactionWasCalled.value shouldBe true
        }
        should("prefer cache") {
            repository.save("key", "value")
            cut.get("key").first() shouldBe "value"
            repository.save("key", "value2")
            cut.get("key").first() shouldBe "value"
            transactionWasCalled.value shouldBe true
        }
    }
    context("update") {
        should("read from database") {
            repository.save("key", "old")
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
            transactionWasCalled.value shouldBe true
        }
        should("prefer cache") {
            repository.save("key", "old")
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
            repository.save("key", "dino")
            cut.update("key") {
                it shouldBe "value"
                "new value"
            }
            transactionWasCalled.value shouldBe true
        }
        should("save to database") {
            repository.save("key", "old")
            cut.update("key") { "value" }
            repository.get("key") shouldBe "value"
            transactionWasCalled.value shouldBe true
        }
        should("allow multiple writes") {
            repository.save("key", "old")
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
            repository.get("key") shouldBeOneOf listOf("value1", "value2")
            transactionWasCalled.value shouldBe true
        }
        should("remove from database") {
            repository.save("key", "old")
            cut.update("key") { null }
            repository.get("key") shouldBe null
            transactionWasCalled.value shouldBe true
        }
        should("not save to repository when flag is set") {
            repository.save("key", "old")
            cut.update("key", persistIntoRepository = false) { "value" }
            repository.get("key") shouldBe "old"
        }
    }
})