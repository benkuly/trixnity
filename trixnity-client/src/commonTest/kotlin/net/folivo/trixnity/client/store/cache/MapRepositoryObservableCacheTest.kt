package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.store.repository.InMemoryMapRepository
import net.folivo.trixnity.client.store.repository.MapRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.js.JsName
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
class MapRepositoryObservableCacheTest : ShouldSpec({
    timeout = 5_000
    class TestClock() : Clock {
        @JsName("nowVar")
        var now: Instant = Instant.fromEpochMilliseconds(0)
        override fun now(): Instant = now
    }

    lateinit var cacheScope: CoroutineScope
    lateinit var clock: TestClock
    lateinit var repository: MapRepository<String, String, String>
    lateinit var cut: MapRepositoryObservableCache<String, String, String>
    val readTransactionCalled = MutableStateFlow(0)
    val writeTransactionCalled = MutableStateFlow(0)
    val tm = object : RepositoryTransactionManager {
        override suspend fun <T> readTransaction(block: suspend () -> T): T {
            return block().also { readTransactionCalled.value++ }
        }

        override suspend fun writeTransaction(block: suspend () -> Unit) {
            block()
            writeTransactionCalled.value++
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        clock = TestClock()
        readTransactionCalled.value = 0
        writeTransactionCalled.value = 0
        repository = object : InMemoryMapRepository<String, String, String>() {
            override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
        }
        cut = MapRepositoryObservableCache(repository, tm, cacheScope, clock)
    }
    afterTest {
        cacheScope.cancel()
    }

    context("write") {
        should("save into database without reading old value") {
            repository.save("firstKey", "secondKey1", "old")
            repository.save("firstKey", "secondKey2", "old")
            cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
            readTransactionCalled.value shouldBe 0
            writeTransactionCalled.value shouldBe 1
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "old")
        }
        should("save existing cache value without reading old value") {
            repository.save("firstKey", "secondKey1", "old")
            cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), "value1")
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1")
            cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2"), "value2")
            readTransactionCalled.value shouldBe 0
            writeTransactionCalled.value shouldBe 2
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        }
        should("delete without reading old value") {
            repository.save("firstKey", "secondKey1", "old")
            repository.save("firstKey", "secondKey2", "old")
            cut.set(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1"), null)
            readTransactionCalled.value shouldBe 0
            writeTransactionCalled.value shouldBe 1
            repository.get("firstKey") shouldBe mapOf("secondKey2" to "old")
        }
        should("handle massive parallel manipulation of same key") {
            val database = MutableSharedFlow<String?>(replay = 3000)

            class InMemoryRepositoryWithHistory : InMemoryMapRepository<String, String, String>() {
                override suspend fun save(firstKey: String, secondKey: String, value: String) {
                    database.emit(value)
                    super.save(firstKey, secondKey, value)
                }

                override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
            }
            cut = MapRepositoryObservableCache(InMemoryRepositoryWithHistory(), tm, cacheScope, clock)
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
        should("handle massive parallel manipulation of different keys") {
            val database = MutableSharedFlow<Pair<String, String>?>(replay = 3000)

            class InMemoryRepositoryWithHistory : InMemoryMapRepository<String, String, String>() {
                override suspend fun save(firstKey: String, secondKey: String, value: String) {
                    database.emit(firstKey to secondKey)
                    super.save(firstKey, secondKey, value)
                }

                override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
            }
            cut = MapRepositoryObservableCache(InMemoryRepositoryWithHistory(), tm, cacheScope, clock)
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
            timePerOperation shouldBeLessThan 20.milliseconds
            completeTime shouldBeLessThan 200.milliseconds
        }
    }
    context("write with update") {
        should("save into database reading old value") {
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
        should("delete into database reading old value") {
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
    }
    context("read") {
        should("load from database, when not exists in cache") {
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
            repository.save("firstKey", "secondKey1", "new1")
            repository.save("firstKey", "secondKey2", "new2")
            cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "old1"
            cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey2")).first() shouldBe "new2"
        }
        should("prefer cache") {
            cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) { "value1" }
            repository.delete("firstKey", "secondKey1")
            cut.get(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")).first() shouldBe "value1"
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
            cut = MapRepositoryObservableCache(repository, tm, cacheScope, clock, expireDuration = 1.seconds)
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
            clock.now += (1.seconds + 1.milliseconds)
            cut.invalidate() // should not remove secondKey1
            collectReadByFirstKey.cancelAndJoin()
            delay(50.milliseconds) // wait for cancel to take effect

            clock.now += (1.seconds + 1.milliseconds)
            cut.invalidate() // should remove secondKey2

            repository.save("firstKey", "secondKey1", "new1")
            repository.save("firstKey", "secondKey2", "new2")
            cut.readByFirstKey("firstKey").flatten().first() shouldBe mapOf(
                "secondKey1" to "old1",
                "secondKey2" to "new2"
            )
            collectRead.cancel()
        }
        should("load from database, when only partially exists in cache") {
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
        should("prefer cache") {
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
        should("prefer cache even when values are added in cache") {
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
        should("remove from cache when not used anymore") {
            cut = MapRepositoryObservableCache(repository, tm, cacheScope, clock, expireDuration = 1.seconds)
            val readScope1 = CoroutineScope(Dispatchers.Default)
            repository.save("firstKey", "secondKey1", "old1")
            cut.readByFirstKey(key = "firstKey").flatten().stateIn(readScope1).value shouldBe
                    mapOf("secondKey1" to "old1")
            repository.save("firstKey", "secondKey1", "new1")
            readScope1.cancel()
            delay(50.milliseconds) // wait for cancel to take effect

            clock.now += 1.seconds
            cut.invalidate()
            cut.readByFirstKey(key = "firstKey").flatten().first() shouldBe
                    mapOf("secondKey1" to "old1")

            clock.now += 1.milliseconds
            cut.invalidate()
            cut.readByFirstKey(key = "firstKey").flatten().first() shouldBe
                    mapOf("secondKey1" to "new1")
        }
        should("remove from cache when stale") {
            cut = MapRepositoryObservableCache(repository, tm, cacheScope, clock, expireDuration = 1.seconds)
            val readScope = CoroutineScope(Dispatchers.Default)
            repository.save("firstKey", "secondKey1", "old1")
            repository.save("firstKey", "secondKey2", "old2")
            val byFirstKey = cut.readByFirstKey(key = "firstKey").map { it.keys }.stateIn(readScope)
            byFirstKey.value shouldBe setOf("secondKey1", "secondKey2")
            cut.update(MapRepositoryCoroutinesCacheKey("firstKey", "secondKey1")) { null }
            clock.now += (1.seconds + 1.milliseconds)
            cut.invalidate()
            delay(50.milliseconds) // wait for collecting
            byFirstKey.value shouldBe setOf("secondKey2")
            readScope.cancel()
        }
        should("handle parallel read and write") {
            repository = object : InMemoryMapRepository<String, String, String>() {
                override suspend fun save(firstKey: String, secondKey: String, value: String) {
                    delay(50.milliseconds)
                    super.save(firstKey, secondKey, value)
                }

                override fun serializeKey(firstKey: String, secondKey: String): String = firstKey + secondKey
            }
            cut = MapRepositoryObservableCache(repository, tm, cacheScope, clock)
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
    }
    context("index") {
        should("has right subscription count") {
            val values =
                ConcurrentObservableMap<MapRepositoryCoroutinesCacheKey<String, String>, MutableStateFlow<CacheValue<String?>>>()
            cut = MapRepositoryObservableCache(
                repository = repository,
                tm = tm,
                cacheScope = cacheScope,
                clock = clock,
                expireDuration = 50.milliseconds,
                values = values
            )
            val subscriptionCountScope = CoroutineScope(Dispatchers.Default)
            suspend fun subscriptionCount1() =
                values.getIndexSubscriptionCount(MapRepositoryCoroutinesCacheKey("firstKey1", "secondsKey1"))

            suspend fun subscriptionCount2() =
                values.getIndexSubscriptionCount(MapRepositoryCoroutinesCacheKey("firstKey2", "secondsKey1"))
            subscriptionCount1() shouldBe 0
            subscriptionCount2() shouldBe 0

            cut.set(MapRepositoryCoroutinesCacheKey("firstKey1", "secondsKey1"), "value")
            subscriptionCount1() shouldBe 0
            subscriptionCount2() shouldBe 0

            val readByFirstKeyScope = CoroutineScope(Dispatchers.Default)

            cut.readByFirstKey("firstKey1").flatten().stateIn(readByFirstKeyScope)
            delay(50.milliseconds)
            subscriptionCount1() shouldBe 1
            subscriptionCount2() shouldBe 0

            readByFirstKeyScope.cancel()
            delay(50.milliseconds)
            subscriptionCount1() shouldBe 0
            subscriptionCount2() shouldBe 0

            subscriptionCountScope.cancel()
        }
    }
})