package net.folivo.trixnity.client.store.cache

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.store.repository.InMemoryMapRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTimedValue

class MapRepositoryObservableCacheTest : TrixnityBaseTest() {

    private val repository = object : InMemoryMapRepository<String, String, String>() {
        override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
    }

    private val readTransactionCalled = MutableStateFlow(0)
    private val writeTransactionCalled = MutableStateFlow(0)
    private val tm = object : RepositoryTransactionManager {
        override suspend fun <T> readTransaction(block: suspend () -> T): T {
            return block().also { readTransactionCalled.value++ }
        }

        override suspend fun writeTransaction(block: suspend () -> Unit) {
            block()
            writeTransactionCalled.value++
        }
    }

    private val cut = MapRepositoryObservableCache(repository, tm, testScope.backgroundScope, testScope.testClock)


    @Test
    fun `write » save into database without reading old value`() = runTest {
        repository.save("firstKey", "secondKey1", "old")
        repository.save("firstKey", "secondKey2", "old")
        cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
        readTransactionCalled.value shouldBe 0
        writeTransactionCalled.value shouldBe 1
        repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "old")
    }

    @Test
    fun `write » save existing cache value without reading old value`() = runTest {
        repository.save("firstKey", "secondKey1", "old")
        cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
        repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1")
        cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2"), "value2")
        readTransactionCalled.value shouldBe 0
        writeTransactionCalled.value shouldBe 2
        repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
    }

    @Test
    fun `write » delete without reading old value`() = runTest {
        repository.save("firstKey", "secondKey1", "old")
        repository.save("firstKey", "secondKey2", "old")
        cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), null)
        readTransactionCalled.value shouldBe 0
        writeTransactionCalled.value shouldBe 1
        repository.get("firstKey") shouldBe mapOf("secondKey2" to "old")
    }

    @Test
    fun `write » handle massive parallel manipulation of same key`() = runTest {
        val database = MutableSharedFlow<String?>(replay = 3000)

        class InMemoryRepositoryWithHistory : InMemoryMapRepository<String, String, String>() {
            override suspend fun save(firstKey: String, secondKey: String, value: String) {
                database.emit(value)
                super.save(firstKey, secondKey, value)
            }

            override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
        }

        val cut = MapRepositoryObservableCache(InMemoryRepositoryWithHistory(), tm, backgroundScope, testClock)
        val (operationsTimeSum, completeTime) =
            measureTimedValue {
                (0..99).map { i ->
                    async {
                        measureTimedValue {
                            cut.update(
                                key = MapRepositoryCoroutinesCacheKey("key", "key"),
                                updater = { "$i" },
                            )
                        }.duration
                    }
                }.awaitAll().reduce { acc, duration -> acc + duration }
            }
        database.replayCache shouldContainAll (0..99).map { it.toString() }
        val timePerOperation = operationsTimeSum / 100
        println("timePerOperation=$timePerOperation completeTime=$completeTime")
        timePerOperation shouldBeLessThan 20.milliseconds
        completeTime shouldBeLessThan 200.milliseconds
    }

    @Test
    fun `write » handle massive parallel manipulation of different keys`() = runTest {
        val database = MutableSharedFlow<Pair<String, String>?>(replay = 3000)

        class InMemoryRepositoryWithHistory : InMemoryMapRepository<String, String, String>() {
            override suspend fun save(firstKey: String, secondKey: String, value: String) {
                database.emit(firstKey to secondKey)
                super.save(firstKey, secondKey, value)
            }

            override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
        }

        val cut = MapRepositoryObservableCache(InMemoryRepositoryWithHistory(), tm, backgroundScope, testClock)
        val (operationsTimeSum, completeTime) =
            measureTimedValue {
                coroutineScope {
                    (0..99).map { i ->
                        async {
                            measureTimedValue {
                                cut.update(
                                    key = MapRepositoryCoroutinesCacheKey("key", "$i"),
                                    updater = { "value" },
                                )
                            }.duration
                        }
                    }.awaitAll().reduce { acc, duration -> acc + duration }
                }
            }
        database.replayCache shouldContainAll (0..99).map { "key" to "$it" }

        val timePerOperation = operationsTimeSum / 100
        println("timePerOperation=$timePerOperation completeTime=$completeTime")
        timePerOperation shouldBeLessThan 40.milliseconds
        completeTime shouldBeLessThan 600.milliseconds
    }

    @Test
    fun `write with update » save into database reading old value`() = runTest {
        repository.save("firstKey", "secondKey1", "old")
        cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
            it shouldBe "old"
            "value1"
        }
        // we overwrite the repository to check, that only secondKey1 is updated
        repository.save("firstKey", "secondKey1", "old")
        repository.save("firstKey", "secondKey2", "old")
        cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
            it shouldBe "value1"
            "value2"
        }
        repository.get("firstKey") shouldBe mapOf("secondKey1" to "value2", "secondKey2" to "old")
    }

    @Test
    fun `write with update » delete into database reading old value`() = runTest {
        repository.save("firstKey", "secondKey1", "old")
        cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
            it shouldBe "old"
            null
        }
        repository.get("firstKey") shouldBe mapOf()
        repository.save("firstKey", "secondKey1", "old")
        cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) {
            it shouldBe null
            null
        }
    }

    @Test
    fun `read » load from database when not exists in cache`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
        repository.save("firstKey", "secondKey1", "new1")
        repository.save("firstKey", "secondKey2", "new2")
        cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
        cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2")).first() shouldBe "new2"
    }

    @Test
    fun `read » prefer cache`() = runTest {
        cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) { "value1" }
        repository.delete("firstKey", "secondKey1")
        cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "value1"
    }

    @Test
    fun `readByFirstKey » load from database when not exists in cache`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "old2"
        )
    }

    @Test
    fun `readByFirstKey » load from database when cache values removed`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        val startedCollectReadByFirstKey = MutableStateFlow(false)
        val collectRead = launch(start = CoroutineStart.LAZY) {
            cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"))
                .onEach { startedCollectReadByFirstKey.value = true }
                .collect()
        }
        val collectReadByFirstKey = launch {
            cut.readByFirstKey("firstKey").flatten()
                .onEach { collectRead.start() }
                .collect()
        }
        startedCollectReadByFirstKey.first { it }
        delay(1.minutes + 1.milliseconds)
        cut.invalidate() // should not remove secondKey1
        collectReadByFirstKey.cancelAndJoin()
        delay(50.milliseconds) // wait for cancel to take effect

        delay(1.minutes + 1.milliseconds)
        cut.invalidate() // should remove secondKey2

        repository.save("firstKey", "secondKey1", "new1")
        repository.save("firstKey", "secondKey2", "new2")
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "new2"
        )
        collectRead.cancel()
    }

    @Test
    fun `readByFirstKey » load from database when only partially exists in cache`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
        cut.invalidate()
        readTransactionCalled.value = 0
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "old2"
        )
        readTransactionCalled.value shouldBe 1
    }

    @Test
    fun `readByFirstKey » prefer cache`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "old2"
        )
        repository.save("firstKey", "secondKey1", "new1")
        repository.save("firstKey", "secondKey2", "new2")
        repository.save("firstKey", "secondKey3", "new3")
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "old2"
        )
    }

    @Test
    fun `readByFirstKey » prefer cache even when values are added in cache`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "old2"
        )
        repository.save("firstKey", "secondKey1", "new1")
        repository.save("firstKey", "secondKey2", "new2")
        cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey3"), "new3")
        cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
            "secondKey1" to "old1",
            "secondKey2" to "old2",
            "secondKey3" to "new3",
        )
    }

    @Test
    fun `readByFirstKey » remove from cache when not used anymore`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")

        val readByFirstJob = backgroundScope.launch { cut.readByFirstKey(key = "firstKey").flatten().collect() }

        cut.readByFirstKey(key = "firstKey").flatten().first() shouldBe
                mapOf("secondKey1" to "old1")
        repository.save("firstKey", "secondKey1", "new1")
        readByFirstJob.cancel()

        delay(1.minutes)
        cut.invalidate()
        cut.readByFirstKey(key = "firstKey").flatten().first() shouldBe
                mapOf("secondKey1" to "old1")

        delay(1.milliseconds)
        cut.invalidate()
        cut.readByFirstKey(key = "firstKey").flatten().first() shouldBe
                mapOf("secondKey1" to "new1")
    }

    @Test
    fun `readByFirstKey » remove from cache when stale`() = runTest {
        repository.save("firstKey", "secondKey1", "old1")
        repository.save("firstKey", "secondKey2", "old2")
        val byFirstKey = cut.readByFirstKey(key = "firstKey").map { it.keys }.stateIn(backgroundScope)

        byFirstKey.value shouldBe setOf("secondKey1", "secondKey2")
        cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) { null }
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        delay(50.milliseconds) // wait for collecting
        byFirstKey.value shouldBe setOf("secondKey2")
    }

    @Test
    fun `readByFirstKey » handle parallel read and write`() = runTest {
        val repository = object : InMemoryMapRepository<String, String, String>() {
            override suspend fun save(firstKey: String, secondKey: String, value: String) {
                delay(50.milliseconds)
                super.save(firstKey, secondKey, value)
            }

            override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
        }
        val cut = MapRepositoryObservableCache(repository, tm, backgroundScope, testClock)
        cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondsKey1"), "value1")
        coroutineScope {
            launch {
                cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondsKey2")) { "value2" }
            }
            launch {
                cut.readByFirstKey("firstKey").filterNotNull().first()
            }
        }
        cut.readByFirstKey("firstKey").filterNotNull().first() shouldHaveSize 2
    }

    @Test
    fun `index » has right subscription count`() = runTest {
        val values =
            ConcurrentObservableMap<MapRepositoryCoroutinesCacheKey<String, String>, MutableStateFlow<CacheValue<String?>>>()
        val cut = MapRepositoryObservableCache(
            repository = repository,
            tm = tm,
            cacheScope = backgroundScope,
            clock = testClock,
            expireDuration = 50.milliseconds,
            values = values
        )

        suspend fun subscriptionCount1() =
            values.getIndexSubscriptionCount(MapRepositoryCoroutinesCacheKey("firstKey1", "secondsKey1"))

        suspend fun subscriptionCount2() =
            values.getIndexSubscriptionCount(MapRepositoryCoroutinesCacheKey("firstKey2", "secondsKey1"))
        subscriptionCount1() shouldBe 0
        subscriptionCount2() shouldBe 0

        cut.set(MapRepositoryCoroutinesCacheKey("firstKey1", "secondsKey1"), "value")
        subscriptionCount1() shouldBe 0
        subscriptionCount2() shouldBe 0

        val readByFirstJob = backgroundScope.launch { cut.readByFirstKey("firstKey1").flatten().collect { } }
        delay(50.milliseconds)
        subscriptionCount1() shouldBe 1
        subscriptionCount2() shouldBe 0

        readByFirstJob.cancel()
        delay(50.milliseconds)
        subscriptionCount1() shouldBe 0
        subscriptionCount2() shouldBe 0
    }

}