package net.folivo.trixnity.client.store.cache

import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.InMemoryMinimalRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test

class MinimalRepositoryObservableCacheTest : TrixnityBaseTest() {

    private val repository = object : InMemoryMinimalRepository<String, String>() {
        override fun serializeKey(key: String): String = key
    }
    private val tm = object : RepositoryTransactionManager {
        override suspend fun <T> readTransaction(block: suspend () -> T): T {
            return block().also { readTransactionWasCalled.value = true }
        }

        override suspend fun writeTransaction(block: suspend () -> Unit) {
            block()
            writeTransactionWasCalled.value = true
        }
    }

    private val cut = MinimalRepositoryObservableCache(
        repository,
        tm,
        testScope.backgroundScope,
        testScope.testClock,
    )

    val readTransactionWasCalled = MutableStateFlow(false)
    val writeTransactionWasCalled = MutableStateFlow(false)

    @Test
    fun `get » read from database`() = runTest {
        repository.save("key", "value")
        cut.get("key").first() shouldBe "value"
        readTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `get » prefer cache`() = runTest {
        repository.save("key", "value")
        cut.get("key").first() shouldBe "value"
        repository.save("key", "value2")
        cut.get("key").first() shouldBe "value"
        readTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `save » save into database without reading old null value`() = runTest {
        cut.set("key", "value1")
        cut.set("key", "value2")
        readTransactionWasCalled.value shouldBe false
        writeTransactionWasCalled.value shouldBe true
        repository.get("key") shouldBe "value2"
    }

    @Test
    fun `save » save into database without reading old value`() = runTest {
        repository.save("key", "value1")
        cut.set("key", "value2")
        cut.set("key", "value3")
        readTransactionWasCalled.value shouldBe false
        writeTransactionWasCalled.value shouldBe true
        repository.get("key") shouldBe "value3"
    }

    @Test
    fun `update » read from database`() = runTest {
        repository.save("key", "old")
        cut.update("key") {
            it shouldBe "old"
            "value"
        }
        readTransactionWasCalled.value shouldBe true
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » prefer cache`() = runTest {
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

    @Test
    fun `update » save to database`() = runTest {
        repository.save("key", "old")
        cut.update("key") { "value" }
        repository.get("key") shouldBe "value"
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » allow multiple writes`() = runTest {
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

    @Test
    fun `update » remove from database`() = runTest {
        repository.save("key", "old")
        cut.update("key") { null }
        repository.get("key") shouldBe null
        writeTransactionWasCalled.value shouldBe true
    }

    @Test
    fun `update » not save to repository when flag is set`() = runTest {
        repository.save("key", "old")
        cut.update("key", persistEnabled = false) { "value" }
        repository.get("key") shouldBe "old"
    }
}