package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.js.JsName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class ObservableCacheTest : ShouldSpec({
    timeout = 5_000

    class TestClock() : Clock {
        @JsName("nowVar")
        var now: Instant = Instant.fromEpochMilliseconds(0)
        override fun now(): Instant = now
    }

    lateinit var cacheScope: CoroutineScope
    lateinit var clock: TestClock
    lateinit var cacheStore: InMemoryObservableCacheStore<String, String>
    lateinit var cut: ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        clock = TestClock()
        cacheStore = InMemoryObservableCacheStore()
        cut = ObservableCache("", cacheStore, cacheScope, clock)
    }
    afterTest {
        cacheScope.cancel()
    }

    should("use same internal StateFlow when initial value is null") {
        val readScope = CoroutineScope(Dispatchers.Default)
        val readFlow = cut.get(key = "key").shareIn(readScope, SharingStarted.Eagerly, 3)
        readFlow.first { it == null }
        cut.update(
            key = "key",
            updater = { null } // this should not create a new internal StateFlow
        )
        cut.update(
            key = "key",
            updater = { "newValue" }
        )
        readFlow.first { it == "newValue" }
        readScope.cancel()
    }
    should("fill value with set while read is active") {
        val startedCollect = MutableStateFlow(false)
        val readResult = async { cut.get("key").onEach { startedCollect.value = true }.filterNotNull().first() }
        startedCollect.first { it }
        cut.set("key", "value")
        readResult.await() shouldBe "value"
    }
    should("fill value with update while read is active") {
        val startedCollect = MutableStateFlow(false)
        val readResult = async { cut.get("key").onEach { startedCollect.value = true }.filterNotNull().first() }
        startedCollect.first { it }
        cut.update("key") { "value" }
        readResult.await() shouldBe "value"
    }
    should("skip cache when no read active") {
        cut.set("key", "value")
        cacheStore.persist("key", "otherValue")
        cut.get("key").first() shouldBe "otherValue"
    }
    context("read") {
        should("read value from repository and update cache") {
            cacheStore.persist("key", "a new value")
            cut.get(key = "key").first() shouldBe "a new value"
        }
        should("prefer cache") {
            cacheStore.persist("key", "a new value")
            cut.get(key = "key").first() shouldBe "a new value"
            cacheStore.persist("key", "a changed value")
            cut.get(key = "key").first() shouldBe "a new value"

        }
        should("remove from cache when not used anymore") {
            cut = ObservableCache("", cacheStore, cacheScope, clock, expireDuration = 1.seconds)
            val readScope1 = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "old value")
            cut.get(key = "key").stateIn(readScope1).value shouldBe "old value"
            clock.now += (1.minutes + 1.milliseconds)
            cut.invalidate()
            cacheStore.persist("key", "new value")
            cut.get(key = "key").first() shouldBe "old value"
            readScope1.cancel()
            delay(50.milliseconds) // wait for cancel to take effect
            clock.now += (1.minutes + 1.milliseconds)
            cut.invalidate()
            cut.get(key = "key").first() shouldBe "new value"
        }
        should("remove from cache, when cache time expired") {
            cut = ObservableCache("", cacheStore, cacheScope, clock, expireDuration = 1.seconds)
            cacheStore.persist("key", "a new value")
            cut.get(key = "key").first() shouldBe "a new value"
            clock.now += (1.minutes + 1.milliseconds)
            cut.invalidate()
            cacheStore.persist("key", "another value")
            cut.get(key = "key").first() shouldBe "another value"
            // we check, that the value is not removed before the time expires
            val readScope = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "yet another value")
            cut.get(key = "key").stateIn(readScope).value shouldBe "another value"
            // and that the value is not removed from cache, when there is a scope, that uses it
            clock.now += (1.minutes + 1.milliseconds)
            cut.invalidate()
            cut.get(key = "key").stateIn(readScope).value shouldBe "another value"
            readScope.cancel()
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = ObservableCache("", cacheStore, cacheScope, clock, expireDuration = Duration.INFINITE)
                cacheStore.persist("key", "a new value")
                val readScope = CoroutineScope(Dispatchers.Default)
                cut.get(key = "key").stateIn(readScope).value shouldBe "a new value"
                readScope.cancel()
                cacheStore.persist("key", "aanother value")
                cut.get(key = "key").first() shouldBe "a new value"
                clock.now += (1.minutes + 1.milliseconds)
                cut.invalidate()
                cut.get(key = "key").first() shouldBe "a new value"
            }
        }
    }
    context("write") {
        should("read value from repository and update cache") {
            cacheStore.persist("key", "from db")
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
            )
            cacheStore.get("key") shouldBe "updated value"
            cut.update(
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
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
            )
            cacheStore.get("key") shouldBe "updated value"
            cacheStore.persist("key", "from db 2")
            cut.update(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
            )
            cacheStore.get("key") shouldBe "updated value 2"
        }
        should("not save unchanged value") {
            cut.update(
                key = "key",
                updater = { "updated value" },
            )
            cacheStore.get("key") shouldBe "updated value"
            cacheStore.persist("key", null)
            cut.update(
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
            cut = ObservableCache("", InMemoryObservableCacheStoreWithHistory(), cacheScope, clock)
            val (operationsTimeSum, completeTime) =
                measureTimedValue {
                    (0..99).map { i ->
                        async {
                            measureTimedValue {
                                cut.update(
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
            timePerOperation shouldBeLessThan 20.milliseconds
            completeTime shouldBeLessThan 200.milliseconds
        }
        should("handle massive parallel manipulation of different keys") {
            val database = MutableSharedFlow<String?>(replay = 3000)

            class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
                override suspend fun persist(key: String, value: String?) {
                    database.emit(key)
                }
            }
            cut = ObservableCache("", InMemoryObservableCacheStoreWithHistory(), cacheScope, clock)
            val (operationsTimeSum, completeTime) =
                measureTimedValue {
                    (0..99).map { i ->
                        async {
                            measureTimedValue {
                                cut.update(
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
                cut = ObservableCache("", cacheStore, cacheScope, clock, expireDuration = 1.seconds)
                cut.update(
                    key = "key",
                    updater = { "updated value" },
                )
                clock.now += (1.minutes + 1.milliseconds)
                cut.invalidate()
                cacheStore.persist("key", null)
                cut.update(
                    key = "key",
                    updater = {
                        it shouldBe null
                        "updated value"
                    },
                )
            }
            should("reset expireDuration on use") {
                cut = ObservableCache("", cacheStore, cacheScope, clock, expireDuration = 1.seconds)
                cut.update(
                    key = "key",
                    updater = { "updated value 1" },
                )
                clock.now += 1.minutes
                cut.invalidate()
                cut.update(
                    key = "key",
                    updater = {
                        it shouldBe "updated value 1"
                        "updated value 2"
                    },
                )
                clock.now += 1.milliseconds
                cut.invalidate()
                cacheStore.persist("key", null)
                cut.update(
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
                cut = ObservableCache("", cacheStore, cacheScope, clock, expireDuration = Duration.INFINITE)
                cut.update(
                    key = "key",
                    updater = { "updated value" },
                )
                clock.now += (1.minutes + 1.milliseconds)
                cut.invalidate()
                cacheStore.persist("key", "a new value")
                cut.update(
                    key = "key",
                    updater = { oldValue ->
                        oldValue shouldBe "updated value"
                        "updated value"
                    },
                )
            }
        }
        context("removeFromCacheOnNull enabled") {
            should("remove from cache when value is null (set)") {
                val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
                cut = ObservableCache("", cacheStore, cacheScope, clock, removeFromCacheOnNull = true, values = values)
                cacheStore.persist("key", "a new value")
                cut.get(key = "key").first() shouldBe "a new value"
                values.getAll().size shouldBe 1
                cut.set("key", null)
                values.getAll().size shouldBe 0
            }
            should("remove from cache when value is null (update)") {
                val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
                cut = ObservableCache("", cacheStore, cacheScope, clock, removeFromCacheOnNull = true, values = values)
                cacheStore.persist("key", "a new value")
                cut.get(key = "key").first() shouldBe "a new value"
                values.getAll().size shouldBe 1
                cut.update("key") { null }
                values.getAll().size shouldBe 0
            }
        }
    }
    context("index") {
        class TestIndexedObservableCache(
            name: String,
            store: InMemoryObservableCacheStore<String, String>,
            cacheScope: CoroutineScope,
            expireDuration: Duration = 1.minutes,
        ) : ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>(
            name, store, cacheScope, clock, expireDuration
        ) {
            val index = object : ObservableCacheIndex<String> {
                var onPut = MutableStateFlow<String?>(null)
                override suspend fun onPut(key: String) {
                    onPut.value = key
                }

                var onSkipPut = MutableStateFlow<String?>(null)
                override suspend fun onSkipPut(key: String) {
                    onSkipPut.value = key
                }

                var onRemove = MutableStateFlow<Pair<String, Boolean>?>(null)
                override suspend fun onRemove(key: String, stale: Boolean) {
                    onRemove.value = key to stale
                }

                var onRemoveAllCalled = MutableStateFlow(false)
                override suspend fun onRemoveAll() {
                    onRemoveAllCalled.value = true
                }

                override suspend fun collectStatistic(): ObservableCacheIndexStatistic? = null

                var getSubscriptionCount = 0
                override suspend fun getSubscriptionCount(key: String): Int {
                    return getSubscriptionCount
                }
            }

            init {
                addIndex(index)
            }
        }

        lateinit var indexedCut: TestIndexedObservableCache
        beforeTest {
            indexedCut = TestIndexedObservableCache("", cacheStore, cacheScope)
        }
        should("call onPut on cache insert") {
            indexedCut.update("key") { "value" }
            indexedCut.index.onPut.value shouldBe "key"
            indexedCut.index.onRemove.value shouldBe null
        }
        should("call onSkipPut on cache skip") {
            indexedCut.set("key", "value")
            indexedCut.index.onSkipPut.value shouldBe "key"
            indexedCut.index.onPut.value shouldBe null
            indexedCut.index.onRemove.value shouldBe null
        }
        should("call not onPut on existing cache value") {
            indexedCut.set("key", "value")
            indexedCut.index.onPut.value = null
            indexedCut.set("key", "value")
            indexedCut.index.onPut.value shouldBe null
        }
        should("call onRemove on cache remove") {
            indexedCut = TestIndexedObservableCache("", cacheStore, cacheScope, 1.seconds)
            indexedCut.update("key") { "value" }
            clock.now += (1.minutes + 1.milliseconds)
            indexedCut.invalidate()
            indexedCut.index.onPut.value shouldBe "key"
            indexedCut.index.onRemove.first() shouldBe ("key" to false)
        }
        should("call onRemoveALl on clear") {
            indexedCut = TestIndexedObservableCache("", cacheStore, cacheScope, 1.seconds)
            indexedCut.set("key", "value")
            indexedCut.clear()
            indexedCut.index.onRemoveAllCalled.value shouldBe true
        }
        should("wait for index subsciptions before remove from cache") {
            indexedCut = TestIndexedObservableCache("", cacheStore, cacheScope, 1.seconds)
            indexedCut.index.getSubscriptionCount = 1
            indexedCut.update("key") { "value" }
            clock.now += (1.minutes + 1.milliseconds)
            indexedCut.invalidate()
            indexedCut.index.onRemove.value shouldBe null
            indexedCut.index.getSubscriptionCount = 0
            clock.now += (1.minutes + 1.milliseconds)
            indexedCut.invalidate()
            indexedCut.index.onRemove.first() shouldBe ("key" to false)
        }
        should("allow remove from cache when index subscriptions > 0 but value==null") {
            indexedCut = TestIndexedObservableCache("", cacheStore, cacheScope, 1.seconds)
            indexedCut.index.getSubscriptionCount = 1
            indexedCut.update("key") { null }
            clock.now += (1.minutes + 1.milliseconds)
            indexedCut.invalidate()
            indexedCut.index.onRemove.first() shouldBe ("key" to true)
        }
    }
})