package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.repository.InMemoryMinimalRepository
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

class MinimalRepositoryObservableCacheTest : ShouldSpec({
    timeout = 5_000
    lateinit var repository: MinimalRepository<String, String>
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: MinimalRepositoryObservableCache<String, String>
    val readTransactionWasCalled = MutableStateFlow(false)
    val writeTransactionWasCalled = MutableStateFlow(false)
    val tm = object : RepositoryTransactionManager {
        override suspend fun <T> readTransaction(block: suspend () -> T): T {
            return block().also { readTransactionWasCalled.value = true }
        }

        override suspend fun writeTransaction(block: suspend () -> Unit) {
            block()
            writeTransactionWasCalled.value = true
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        readTransactionWasCalled.value = false
        writeTransactionWasCalled.value = false
        repository = object : InMemoryMinimalRepository<String, String>() {
            override fun serializeKey(key: String): String = key
        }
        cut = MinimalRepositoryObservableCache(repository, tm, cacheScope, Clock.System)
    }
    afterTest {
        cacheScope.cancel()
    }

    context("get") {
        should("read from database") {
            repository.save("key", "value")
            cut.get("key").first() shouldBe "value"
            readTransactionWasCalled.value shouldBe true
        }
        should("prefer cache") {
            repository.save("key", "value")
            cut.get("key").first() shouldBe "value"
            repository.save("key", "value2")
            cut.get("key").first() shouldBe "value"
            readTransactionWasCalled.value shouldBe true
        }
    }
    context("save") {
        should("save into database without reading old null value") {
            cut.set("key", "value1")
            cut.set("key", "value2")
            readTransactionWasCalled.value shouldBe false
            writeTransactionWasCalled.value shouldBe true
            repository.get("key") shouldBe "value2"
        }
        should("save into database without reading old value") {
            repository.save("key", "value1")
            cut.set("key", "value2")
            cut.set("key", "value3")
            readTransactionWasCalled.value shouldBe false
            writeTransactionWasCalled.value shouldBe true
            repository.get("key") shouldBe "value3"
        }
    }
    context("update") {
        should("read from database") {
            repository.save("key", "old")
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
            readTransactionWasCalled.value shouldBe true
            writeTransactionWasCalled.value shouldBe true
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
            writeTransactionWasCalled.value shouldBe true
        }
        should("save to database") {
            repository.save("key", "old")
            cut.update("key") { "value" }
            repository.get("key") shouldBe "value"
            writeTransactionWasCalled.value shouldBe true
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
            writeTransactionWasCalled.value shouldBe true
        }
        should("remove from database") {
            repository.save("key", "old")
            cut.update("key") { null }
            repository.get("key") shouldBe null
            writeTransactionWasCalled.value shouldBe true
        }
        should("not save to repository when flag is set") {
            repository.save("key", "old")
            cut.update("key", persistEnabled = false) { "value" }
            repository.get("key") shouldBe "old"
        }
    }
})