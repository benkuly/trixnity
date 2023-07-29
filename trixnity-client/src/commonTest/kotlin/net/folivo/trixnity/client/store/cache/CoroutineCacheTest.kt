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
class CoroutineCacheTest : ShouldSpec({
    timeout = 10_000
    lateinit var cacheScope: CoroutineScope
    lateinit var cacheStore: InMemoryCoroutineCacheStore<String, String>
    lateinit var cut: CoroutineCache<String, String, InMemoryCoroutineCacheStore<String, String>>

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        cacheStore = InMemoryCoroutineCacheStore()
        cut = CoroutineCache("", cacheStore, cacheScope)
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
            cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = Duration.ZERO)
            val cache = cut.values.stateIn(cacheScope)
            val readScope1 = CoroutineScope(Dispatchers.Default)
            cacheStore.persist("key", "a new value")
            cut.read(key = "key").stateIn(readScope1).value shouldBe "a new value"
            cache.first { it.isNotEmpty() }
            readScope1.cancel()
            cache.first { it.isEmpty() }
        }
        should("remove from cache, when cache time expired") {
            cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = 20.milliseconds)
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
        should("only remove from cache, when persisted") {
            cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = Duration.ZERO)
            val persisted = MutableStateFlow(false)
            cacheStore.persisted.update { it + ("key" to persisted) }
            cacheStore.persist("key", "value")
            cut.write(key = "key", updater = { "value" })
            cut.read(key = "key").first() shouldBe "value"
            delay(30)
            cacheStore.persist("key", "a new value")
            cut.read(key = "key").first() shouldBe "value"

            persisted.value = true
            delay(30)
            cut.read(key = "key").first() shouldBe "a new value"
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = Duration.INFINITE)
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
        should("also save unchanged value") { // TODO is there a way around it?
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
            cacheStore.get("key") shouldBe "updated value"
        }
        // only execute locally for performance tests
        xshould("handle parallel manipulation of same key") {
            val database = MutableSharedFlow<String?>(replay = 3000)

            class InMemoryCoroutineCacheStoreWithHistory : InMemoryCoroutineCacheStore<String, String>() {
                override suspend fun persist(key: String, value: String?): StateFlow<Boolean>? {
                    database.emit(value)
                    return super.persist(key, value)
                }
            }
            cut = CoroutineCache("", InMemoryCoroutineCacheStoreWithHistory(), cacheScope)
            val time = coroutineScope {
                (0..999).map { i ->
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
            database.replayCache shouldContainAll (0..999).map { it.toString() }
            println("###############")
            println(time / 1000)
            println("###############")
            (time / 1000) shouldBeLessThan 5.milliseconds
        }
        // only execute locally for performance tests
        xshould("handle parallel manipulation of different keys") {
            val database = MutableSharedFlow<String?>(replay = 3000)

            class InMemoryCoroutineCacheStoreWithHistory : InMemoryCoroutineCacheStore<String, String>() {
                override suspend fun persist(key: String, value: String?): StateFlow<Boolean>? {
                    database.emit(key)
                    return super.persist(key, value)
                }
            }
            cut = CoroutineCache("", InMemoryCoroutineCacheStoreWithHistory(), cacheScope)
            val time = coroutineScope {
                (0..999).map { i ->
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
            database.replayCache shouldContainAll (0..999).map { it.toString() }
            println("###############")
            println(time / 1000)
            println("###############")
            (time / 1000) shouldBeLessThan 5.milliseconds
        }
        context("infinite cache not enabled") {
            should("remove from cache, when write cache time expired") {
                cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = 10.milliseconds)
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
                cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = 100.milliseconds)
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
            should("only remove from cache, when persisted") {
                cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = Duration.ZERO)
                val persisted1 = MutableStateFlow(false)
                val persisted2 = MutableStateFlow(false)
                cacheStore.persisted.update { it + ("key" to persisted1) }
                cut.write(
                    key = "key",
                    updater = { "o" },
                )
                cacheStore.persisted.update { it + ("key" to persisted2) }
                cut.write(
                    key = "key",
                    updater = { "value" },
                )
                delay(30)
                cacheStore.persist("key", "a new value")
                cut.read(key = "key").first() shouldBe "value"

                persisted1.value = true
                delay(30)
                cut.read(key = "key").first() shouldBe "value"

                persisted2.value = true
                delay(30)
                cut.read(key = "key").first() shouldBe "a new value"
            }
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = CoroutineCache("", cacheStore, cacheScope, expireDuration = Duration.INFINITE)
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
    }
    context("index") {
        class IndexedCoroutineCache(
            name: String,
            store: InMemoryCoroutineCacheStore<String, String>,
            cacheScope: CoroutineScope,
            expireDuration: Duration = 1.minutes,
        ) : CoroutineCache<String, String, InMemoryCoroutineCacheStore<String, String>>(
            name, store, cacheScope, expireDuration
        ) {
            val index = object : ObservableMapIndex<String> {
                var onPut = MutableStateFlow<String?>(null)
                override suspend fun onPut(key: String) {
                    onPut.value = key
                }

                var onRemove = MutableStateFlow<String?>(null)
                override suspend fun onRemove(key: String) {
                    onRemove.value = key
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

        lateinit var indexedCut: IndexedCoroutineCache
        beforeTest {
            indexedCut = IndexedCoroutineCache("", cacheStore, cacheScope)
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
            indexedCut = IndexedCoroutineCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.write("key", "value")
            indexedCut.index.onPut.value shouldBe "key"
            indexedCut.index.onRemove.first { it == "key" } shouldBe "key"
        }
        should("call onRemoveALl on clear") {
            indexedCut = IndexedCoroutineCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.write("key", "value")
            indexedCut.clear()
            indexedCut.index.onRemoveAllCalled.value shouldBe true
        }
        should("wait for index subsciptions before remove from cache") {
            indexedCut = IndexedCoroutineCache("", cacheStore, cacheScope, Duration.ZERO)
            indexedCut.index.getSubscriptionCount.value = 1
            indexedCut.write("key", "value")
            delay(30)
            indexedCut.index.onRemove.value shouldBe null
            indexedCut.index.getSubscriptionCount.value = 0
            indexedCut.index.onRemove.first { it == "key" } shouldBe "key"
        }
    }
})