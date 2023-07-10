package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.store.repository.InMemoryMapRepository
import net.folivo.trixnity.client.store.repository.MapRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MapRepositoryCoroutineCacheTest : ShouldSpec({
    timeout = 5_000
    lateinit var repository: MapRepository<String, String, String>
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: MapRepositoryCoroutineCache<String, String, String>
    val readOperationWasCalled = MutableStateFlow(false)
    val writeOperationWasCalled = MutableStateFlow(false)
    val tm = object : TransactionManager {
        override suspend fun withAsyncWriteTransaction(block: suspend () -> Unit): StateFlow<Boolean>? =
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
        repository = object : InMemoryMapRepository<String, String, String>() {
            override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
        }
        cut = MapRepositoryCoroutineCache(repository, tm, cacheScope)
    }
    afterTest {
        cacheScope.cancel()
    }

    context("write") {
        should("save into database without reading old value") {
            repository.save("firstKey", "secondKey1", "old")
            repository.save("firstKey", "secondKey2", "old")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
            readOperationWasCalled.value shouldBe false
            writeOperationWasCalled.value shouldBe true
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "old")
        }
        should("save existing cache value without reading old value") {
            repository.save("firstKey", "secondKey1", "old")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2"), "value2")
            readOperationWasCalled.value shouldBe false
            writeOperationWasCalled.value shouldBe true
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "value1",
                "secondKey2" to "value2"
            )
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        }
    }
    context("write with update") {
        should("always save") {
            repository.save("firstKey", "secondKey1", "old")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
                it shouldBe "old"
                "value1"
            }
            // we overwrite the repository to check, that only secondKey1 is updated
            repository.save("firstKey", "secondKey1", "old")
            repository.save("firstKey", "secondKey2", "old")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
                it shouldBe "value1"
                "value2"
            }
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "value2",
                "secondKey2" to "old"
            )
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value2", "secondKey2" to "old")
        }
        should("always delete") {
            repository.save("firstKey", "secondKey1", "old")
            repository.delete("firstKey", "secondKey1")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
                it shouldBe null
                null
            }
            repository.save("firstKey", "secondKey1", "old")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
                it shouldBe null
                null
            }
            repository.get("firstKey") shouldBe mapOf()
            cut.readByFirstKey("firstKey").flatten().first().shouldNotBeNull() shouldHaveSize 0
        }
        should("update existing cache value") {
            repository.save("firstKey", "secondKey1", "old")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
                it shouldBe "old"
                "value1"
            }
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf("secondKey1" to "value1")
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2")) { "value2" }
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "value1",
                "secondKey2" to "value2"
            )
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        }
    }
    context("read") {
        should("load from database, when not exists in cache") {
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            cut.read(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
            repository.save("firstKey", "secondKey1", "new1")
            repository.save("firstKey", "secondKey2", "new2")
            cut.read(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
            cut.read(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2")).first() shouldBe "new2"
        }
        should("prefer cache") {
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
            repository.delete("firstKey", "secondKey1")
            cut.read(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "value1"
        }
    }
    context("readByFirstKey") {
        should("load from database, when not exists in cache") {
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "old2"
            )
        }
        should("load from database, when cache values removed") {
            cut = MapRepositoryCoroutineCache(repository, tm, cacheScope, expireDuration = Duration.ZERO)
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            val stopCollectReadByFirstKey = MutableStateFlow(false)
            val collectRead = launch(start = CoroutineStart.LAZY) {
                cut.read(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"))
                    .onEach { stopCollectReadByFirstKey.value = true }
                    .collect()
            }
            val collectReadByFirstKey = launch {
                cut.readByFirstKey("firstKey").flatten()
                    .onEach { collectRead.start() }
                    .collect()
            }
            stopCollectReadByFirstKey.first { it }
            collectReadByFirstKey.cancel()
            delay(1.seconds)
            repository.save("firstKey", "secondKey1", "new1")
            repository.save("firstKey", "secondKey2", "new2")
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "new2"
            )
            collectRead.cancel()
        }
        should("load from database, when partially exists in cache") {
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            cut.read(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "old2"
            )
        }
        should("prefer cache") {
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "old2"
            )
            repository.save("firstKey", "secondKey1", "new1")
            repository.save("firstKey", "secondKey2", "new2")
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "old2"
            )
        }
        should("prefer cache, even when values are added") {
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "old2"
            )
            cut.write(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey3"), "new3")
            repository.deleteAll()
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "old2",
                "secondKey3" to "new3"
            )
        }
    }
})