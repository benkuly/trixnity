package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.repository.InMemoryMinimalRepository
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager

class MinimalRepositoryStateFlowCacheTest : ShouldSpec({
    timeout = 5_000
    lateinit var repository: MinimalRepository<String, String>
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: MinimalRepositoryStateFlowCache<String, String, MinimalRepository<String, String>>
    val readOperationWasCalled = MutableStateFlow(false)
    val writeOperationWasCalled = MutableStateFlow(false)
    val tm = object : TransactionManager {
        override suspend fun withAsyncWriteTransaction(
            onRollback: suspend () -> Unit,
            wait: Boolean,
            block: suspend () -> Unit
        ): StateFlow<Boolean> =
            throw AssertionError("should not call withWriteTransaction")

        override suspend fun <T> readOperation(block: suspend () -> T): T {
            return block().also { readOperationWasCalled.value = true }
        }

        override suspend fun writeOperation(block: suspend () -> Unit) =
            throw AssertionError("should not call writeOperation")

        override suspend fun writeOperationAsync(key: String, block: suspend () -> Unit): StateFlow<Boolean>? {
            block()
            writeOperationWasCalled.value = true
            return null
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        readOperationWasCalled.value = false
        writeOperationWasCalled.value = false
        repository = object : InMemoryMinimalRepository<String, String>() {
            override fun serializeKey(key: String): String = key
        }
        cut = MinimalRepositoryStateFlowCache(cacheScope, repository, tm)
    }
    afterTest {
        cacheScope.cancel()
    }

    context("get") {
        should("read from database") {
            repository.save("key", "value")
            cut.get("key").first() shouldBe "value"
            readOperationWasCalled.value shouldBe true
        }
        should("prefer cache") {
            repository.save("key", "value")
            cut.get("key").first() shouldBe "value"
            repository.save("key", "value2")
            cut.get("key").first() shouldBe "value"
            readOperationWasCalled.value shouldBe true
        }
    }
    context("save") {
        should("save into database without reading old null value") {
            cut.save("key", "value1")
            cut.save("key", "value2")
            readOperationWasCalled.value shouldBe false
            writeOperationWasCalled.value shouldBe true
            repository.get("key") shouldBe "value2"
        }
        should("save into database without reading old value") {
            repository.save("key", "value1")
            cut.save("key", "value2")
            cut.save("key", "value3")
            readOperationWasCalled.value shouldBe false
            writeOperationWasCalled.value shouldBe true
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
            readOperationWasCalled.value shouldBe true
            writeOperationWasCalled.value shouldBe true
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
            writeOperationWasCalled.value shouldBe true
        }
        should("save to database") {
            repository.save("key", "old")
            cut.update("key") { "value" }
            repository.get("key") shouldBe "value"
            writeOperationWasCalled.value shouldBe true
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
            writeOperationWasCalled.value shouldBe true
        }
        should("remove from database") {
            repository.save("key", "old")
            cut.update("key") { null }
            repository.get("key") shouldBe null
            writeOperationWasCalled.value shouldBe true
        }
        should("not save to repository when flag is set") {
            repository.save("key", "old")
            cut.update("key", persistIntoRepository = false) { "value" }
            repository.get("key") shouldBe "old"
        }
    }
})