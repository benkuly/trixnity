package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
class ObservableCacheTest : ShouldSpec({
    timeout = 10_000
    lateinit var cacheScope: CoroutineScope
    lateinit var cacheStore: InMemoryObservableCacheStore<String, String>
    lateinit var cut: ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        cacheStore = InMemoryObservableCacheStore()
        cut = ObservableCache("", cacheStore, cacheScope)
    }
    afterTest {
        cacheScope.cancel()
    }

    should("use same internal StateFlow when initial value is null") {
        val readFlow = cut.read(
            key = "key",
        ).shareIn(cacheScope, SharingStarted.Eagerly, 3)
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
            cut = ObservableCache("", cacheStore, cacheScope, expireDuration = Duration.ZERO)
            val readScope1 = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "old value")
            cut.read(key = "key").stateIn(readScope1).value shouldBe "old value"
            cacheStore.persist("key", "new value")
            cut.read(key = "key").first() shouldBe "old value"
            readScope1.cancel()
            delay(10)
            cut.read(key = "key").first() shouldBe "new value"
        }
        should("remove from cache, when cache time expired") {
            cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 20.milliseconds)
            cacheStore.persist("key", "a new value")
            cut.read(key = "key").first() shouldBe "a new value"
            delay(50)
            cacheStore.persist("key", "another value")
            cut.read(key = "key").first() shouldBe "another value"
            // we check, that the value is not removed before the time expires
            val readScope = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "yet another value")
            cut.read(key = "key").stateIn(readScope).value shouldBe "another value"
            // and that the value is not removed from cache, when there is a scope, that uses it
            delay(50)
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
                delay(50)
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
        xshould("handle massive parallel manipulation of same key") {
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
        xshould("handle massive parallel manipulation of different keys") {
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
                cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 10.milliseconds)
                cut.write(
                    key = "key",
                    updater = { "updated value" },
                )
                delay(50)
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
                cut = ObservableCache("", cacheStore, cacheScope, expireDuration = 100.milliseconds)
                cut.write(
                    key = "key",
                    updater = { "updated value 1" },
                )
                delay(60)
                cut.write(
                    key = "key",
                    updater = {
                        it shouldBe "updated value 1"
                        "updated value 2"
                    },
                )
                delay(60)
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
                delay(30)
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
                val values = ConcurrentMap<String, ObservableCacheValue<String?>>()
                cut = ObservableCache(
                    "",
                    cacheStore,
                    cacheScope,
                    expireDuration = 20.milliseconds,
                    removeFromCacheOnNull = true,
                    values = values
                )
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
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.write("key", "value")
            indexedCut.index.onPut.value shouldBe "key"
            indexedCut.index.onRemove.first { it == "key" to false }
        }
        should("call onRemoveALl on clear") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.write("key", "value")
            indexedCut.clear()
            indexedCut.index.onRemoveAllCalled.value shouldBe true
        }
        should("wait for index subsciptions before remove from cache") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.index.getSubscriptionCount.value = 1
            indexedCut.write("key", "value")
            delay(30)
            indexedCut.index.onRemove.value shouldBe null
            indexedCut.index.getSubscriptionCount.value = 0
            indexedCut.index.onRemove.first { it == "key" to false }
        }
        should("allow remove from cache when index subscriptions > 0 but value==null") {
            indexedCut = IndexedObservableCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.index.getSubscriptionCount.value = 1
            indexedCut.write("key", null)
            delay(30)
            indexedCut.index.onRemove.first { it == "key" to true }
        }
    }
})