package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTimedValue

class ObservableCacheTest : ShouldSpec({
    timeout = 5_000

    lateinit var testCoroutineScheduler: TestCoroutineScheduler
    lateinit var testDispatcher: TestDispatcher
    lateinit var cacheScope: CoroutineScope
    lateinit var cacheStore: InMemoryObservableCacheStore<String, String>
    lateinit var cut: ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>

    beforeTest {
        testDispatcher = StandardTestDispatcher()
        testCoroutineScheduler = testDispatcher.scheduler
        cacheScope = CoroutineScope(testDispatcher)
        cacheStore = InMemoryObservableCacheStore()
        cut = ObservableCache("", cacheStore, cacheScope)
    }
    afterTest {
        testCoroutineScheduler.cancel()
        testDispatcher.cancel()
        cacheScope.cancel()
    }

    should("use same internal StateFlow when initial value is null") {
        val readScope = CoroutineScope(Dispatchers.Default)
        val readFlow = cut.read(key = "key").shareIn(readScope, SharingStarted.Eagerly, 3)
        readFlow.first { it == null }
        cut.write(
            key = "key",
            updater = { null } // this should not create a new internal StateFlow
        )
        cut.write(
            key = "key",
            updater = { "newValue" }
        )
        readFlow.first { it == "newValue" }
        readScope.cancel()
    }
    should("fill value with write while read is active") {
        val readResult = async { cut.read("key").filterNotNull().first() }
        testCoroutineScheduler.advanceUntilIdle()
        cut.write("key", "value")
        readResult.await() shouldBe "value"
    }
    context("read") {
        should("read value from repository and update cache") {
            cacheStore.persist("key", "a new value")
            cut.read(key = "key").first() shouldBe "a new value"
        }
        should("prefer cache") {
            cacheStore.persist("key", "a new value")
            cut.read(key = "key").first() shouldBe "a new value"
            cacheStore.persist("key", "a changed value")
            cut.read(key = "key").first() shouldBe "a new value"

        }
        should("remove from cache when not used anymore") {
            cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 50.milliseconds)
            val readScope1 = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "old value")
            cut.read(key = "key").stateIn(readScope1).value shouldBe "old value"
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            cacheStore.persist("key", "new value")
            cut.read(key = "key").first() shouldBe "old value"
            readScope1.cancel()
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            cut.read(key = "key").first() shouldBe "new value"
        }
        should("remove from cache, when cache time expired") {
            cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 50.milliseconds)
            cacheStore.persist("key", "a new value")
            cut.read(key = "key").first() shouldBe "a new value"
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            cacheStore.persist("key", "another value")
            cut.read(key = "key").first() shouldBe "another value"
            // we check, that the value is not removed before the time expires
            val readScope = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "yet another value")
            cut.read(key = "key").stateIn(readScope).value shouldBe "another value"
            // and that the value is not removed from cache, when there is a scope, that uses it
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            cut.read(key = "key").stateIn(readScope).value shouldBe "another value"
            readScope.cancel()
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = ObservableCache("", cacheStore, cacheScope, expireDuration = Duration.INFINITE)
                cacheStore.persist("key", "a new value")
                val readScope = CoroutineScope(Dispatchers.Default)
                cut.read(key = "key").stateIn(readScope).value shouldBe "a new value"
                readScope.cancel()
                cacheStore.persist("key", "aanother value")
                cut.read(key = "key").first() shouldBe "a new value"
                testCoroutineScheduler.advanceTimeBy(100.milliseconds)
                cut.read(key = "key").first() shouldBe "a new value"
            }
        }
    }
    context("write") {
        should("read value from repository and update cache") {
            cacheStore.persist("key", "from db")
            cut.write(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
            )
            cacheStore.get("key") shouldBe "updated value"
            cut.write(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
            )
            cacheStore.get("key") shouldBe "updated value 2"
        }
        should("prefer cache") {
            cacheStore.persist("key", "from db")
            cut.write(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
            )
            cacheStore.get("key") shouldBe "updated value"
            cacheStore.persist("key", "from db 2")
            cut.write(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
            )
            cacheStore.get("key") shouldBe "updated value 2"
        }
        should("not save unchanged value") {
            cut.write(
                key = "key",
                updater = { "updated value" },
            )
            cacheStore.get("key") shouldBe "updated value"
            cacheStore.persist("key", null)
            cut.write(
                key = "key",
                updater = { "updated value" },
            )
            cacheStore.get("key") shouldBe null
        }
        should("handle massive parallel manipulation of same key") {
            val database = MutableSharedFlow<String?>(replay = 3000)

            class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
                override suspend fun persist(key: String, value: String?) {
                    database.emit(value)
                }
            }
            cut = ObservableCache("", InMemoryObservableCacheStoreWithHistory(), cacheScope)
            val (operationsTimeSum, completeTime) =
                measureTimedValue {
                    (0..99).map { i ->
                        async {
                            measureTimedValue {
                                cut.write(
                                    key = "key",
                                    updater = { "$i" },
                                )
                            }.duration
                        }
                    }.awaitAll().reduce { acc, duration -> acc + duration }
                }
            database.replayCache shouldContainAll (0..99).map { it.toString() }
            val timePerOperation = operationsTimeSum / 100
            println("timePerOperation=$timePerOperation completeTime=$completeTime")
            timePerOperation shouldBeLessThan 10.milliseconds
            completeTime shouldBeLessThan 100.milliseconds
        }
        should("handle massive parallel manipulation of different keys") {
            val database = MutableSharedFlow<String?>(replay = 3000)

            class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
                override suspend fun persist(key: String, value: String?) {
                    database.emit(key)
                }
            }
            cut = ObservableCache("", InMemoryObservableCacheStoreWithHistory(), cacheScope)
            val (operationsTimeSum, completeTime) =
                measureTimedValue {
                    (0..99).map { i ->
                        async {
                            measureTimedValue {
                                cut.write(
                                    key = "$i",
                                    updater = { "value" },
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
        context("infinite cache not enabled") {
            should("remove from cache, when write cache time expired") {
                cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 50.milliseconds)
                cut.write(
                    key = "key",
                    updater = { "updated value" },
                )
                testCoroutineScheduler.advanceTimeBy(100.milliseconds)
                cacheStore.persist("key", null)
                cut.write(
                    key = "key",
                    updater = {
                        it shouldBe null
                        "updated value"
                    },
                )
            }
            should("reset expireDuration on use") {
                cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 50.milliseconds)
                cut.write(
                    key = "key",
                    updater = { "updated value 1" },
                )
                testCoroutineScheduler.advanceTimeBy(40.milliseconds)
                cut.write(
                    key = "key",
                    updater = {
                        it shouldBe "updated value 1"
                        "updated value 2"
                    },
                )
                testCoroutineScheduler.advanceTimeBy(40.milliseconds) // (40 + 40 = 80) > expireDuration
                cacheStore.persist("key", null)
                cut.write(
                    key = "key",
                    updater = {
                        it shouldBe "updated value 2"
                        "updated value"
                    },
                )
            }
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = ObservableCache("", cacheStore, cacheScope, expireDuration = Duration.INFINITE)
                cut.write(
                    key = "key",
                    updater = { "updated value" },
                )
                testCoroutineScheduler.advanceTimeBy(100.milliseconds)
                cacheStore.persist("key", "a new value")
                cut.write(
                    key = "key",
                    updater = { oldValue ->
                        oldValue shouldBe "updated value"
                        "updated value"
                    },
                )
            }
        }
        context("removeFromCacheOnNull enabled") {
            should("remove from cache when value is null") {
                val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
                cut = ObservableCache("", cacheStore, cacheScope, removeFromCacheOnNull = true, values = values)
                cacheStore.persist("key", "a new value")
                cut.read(key = "key").first() shouldBe "a new value"
                values.getAll().size shouldBe 1
                cut.write("key", null)
                values.getAll().size shouldBe 0
            }
        }
    }
    context("index") {
        class IndexedObservableCache(
            name: String,
            store: InMemoryObservableCacheStore<String, String>,
            cacheScope: CoroutineScope,
            expireDuration: Duration = 1.minutes,
        ) : ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>(
            name, store, cacheScope, expireDuration
        ) {
            val index = object : ObservableMapIndex<String> {
                var onPut = MutableStateFlow<String?>(null)
                override suspend fun onPut(key: String) {
                    onPut.value = key
                }

                var onRemove = MutableStateFlow<Pair<String, Boolean>?>(null)
                override suspend fun onRemove(key: String, stale: Boolean) {
                    onRemove.value = key to stale
                }

                var onRemoveAllCalled = MutableStateFlow(false)
                override suspend fun onRemoveAll() {
                    onRemoveAllCalled.value = true
                }

                val getSubscriptionCount = MutableStateFlow(0)
                override suspend fun getSubscriptionCount(key: String): StateFlow<Int> {
                    return getSubscriptionCount
                }
            }

            init {
                addIndex(index)
            }
        }

        lateinit var indexedCut: IndexedObservableCache
        beforeTest {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope)
        }
        should("call onPut on cache insert") {
            indexedCut.write("key", "value")
            indexedCut.index.onPut.value shouldBe "key"
            indexedCut.index.onRemove.value shouldBe null
        }
        should("call not onPut on existing cache value") {
            indexedCut.write("key", "value")
            indexedCut.index.onPut.value = null
            indexedCut.write("key", "value")
            indexedCut.index.onPut.value shouldBe null
        }
        should("call onRemove on cache remove") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, 50.milliseconds)
            indexedCut.write("key", "value")
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            indexedCut.index.onPut.value shouldBe "key"
            indexedCut.index.onRemove.first() shouldBe ("key" to false)
        }
        should("call onRemoveALl on clear") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, 50.milliseconds)
            indexedCut.write("key", "value")
            indexedCut.clear()
            indexedCut.index.onRemoveAllCalled.value shouldBe true
        }
        should("wait for index subsciptions before remove from cache") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, 50.milliseconds)
            indexedCut.index.getSubscriptionCount.value = 1
            indexedCut.write("key", "value")
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            indexedCut.index.onRemove.value shouldBe null
            indexedCut.index.getSubscriptionCount.value = 0
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            indexedCut.index.onRemove.first() shouldBe ("key" to false)
        }
        should("allow remove from cache when index subscriptions > 0 but value==null") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, 50.milliseconds)
            indexedCut.index.getSubscriptionCount.value = 1
            indexedCut.write("key", null)
            testCoroutineScheduler.advanceTimeBy(100.milliseconds)
            indexedCut.index.onRemove.first() shouldBe ("key" to true)
        }
    }
})