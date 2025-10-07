package net.folivo.trixnity.client.store.cache

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.TransactionManagerImpl
import net.folivo.trixnity.client.store.repository.NoOpRepositoryTransactionManager
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTimedValue

class ObservableCacheTest : TrixnityBaseTest() {

    private val cacheStore = InMemoryObservableCacheStore<String, String>()

    private val cut = ObservableCache("test", cacheStore, testScope.backgroundScope, testScope.testClock)
    private val indexedCut by lazy {
        TestIndexedObservableCache(
            "",
            cacheStore,
            testScope.backgroundScope,
            testScope.testClock
        )
    }


    @Test
    fun `read » read value from repository and update cache`() = runTest {
        cacheStore.persist("key", "a new value")
        cut.get(key = "key").first() shouldBe "a new value"
    }

    @Test
    fun `read » prefer cache`() = runTest {
        cacheStore.persist("key", "a new value")
        cut.get(key = "key").first() shouldBe "a new value"
        cacheStore.persist("key", "a changed value")
        cut.get(key = "key").first() shouldBe "a new value"

    }

    @Test
    fun `read » remove from cache when not used anymore`() = runTest {
        cacheStore.persist("key", "old value")

        val collectingJob = backgroundScope.launch {
            cut.get(key = "key").collect()
        }

        cut.get(key = "key").first() shouldBe "old value"
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cacheStore.persist("key", "new value")
        cut.get(key = "key").first() shouldBe "old value"

        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").first() shouldBe "old value"

        collectingJob.cancel()
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").first() shouldBe "new value"
    }

    @Test
    fun `read » remove from cache when cache time expired`() = runTest {
        cacheStore.persist("key", "a new value")
        cut.get(key = "key").first() shouldBe "a new value"
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cacheStore.persist("key", "another value")
        cut.get(key = "key").first() shouldBe "another value"
        // we check, that the value is not removed before the time expires
        cacheStore.persist("key", "yet another value")
        cut.get(key = "key").stateIn(backgroundScope).value shouldBe "another value"
        // and that the value is not removed from cache, when there is a scope, that uses it
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").stateIn(backgroundScope).value shouldBe "another value"
    }

    @Test
    fun `read » infinite cache enabled » never remove from cache`() = runTest {
        val cut = ObservableCache("", cacheStore, backgroundScope, testClock, expireDuration = Duration.INFINITE)
        cacheStore.persist("key", "a new value")
        cut.get(key = "key").stateIn(backgroundScope).value shouldBe "a new value"
        cacheStore.persist("key", "aanother value")
        cut.get(key = "key").first() shouldBe "a new value"
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.get(key = "key").first() shouldBe "a new value"
    }

    @Test
    fun `write » read value from repository and update cache`() = runTest {
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

    @Test
    fun `write » prefer cache`() = runTest {
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

    @Test
    fun `write » not save unchanged value`() = runTest {
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

    @Test
    fun `write » handle massive parallel manipulation of same key`() = runTest {
        val database = MutableSharedFlow<String?>(replay = 3000)

        class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
            override suspend fun persist(key: String, value: String?) {
                database.emit(value)
            }
        }

        val cut = ObservableCache("", InMemoryObservableCacheStoreWithHistory(), backgroundScope, testClock)
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

    @Test
    fun `write » handle massive parallel manipulation of different keys`() = runTest {
        val database = MutableSharedFlow<String?>(replay = 3000)

        class InMemoryObservableCacheStoreWithHistory : InMemoryObservableCacheStore<String, String>() {
            override suspend fun persist(key: String, value: String?) {
                database.emit(key)
            }
        }

        val cut = ObservableCache("", InMemoryObservableCacheStoreWithHistory(), backgroundScope, testClock)
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
        timePerOperation shouldBeLessThan 30.milliseconds
        completeTime shouldBeLessThan 300.milliseconds
    }

    @Test
    fun `write » use same internal StateFlow when initial value is null`() = runTest {
        val readFlow = cut.get(key = "key").shareIn(backgroundScope, SharingStarted.Eagerly, 3)
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
    }

    @Test
    fun `write » fill value with set while read is active`() = runTest {
        val startedCollect = MutableStateFlow(false)
        val readResult = async { cut.get("key").onEach { startedCollect.value = true }.filterNotNull().first() }
        startedCollect.first { it }
        cut.set("key", "value")
        readResult.await() shouldBe "value"
    }

    @Test
    fun `write » fill value with set when cache entry present`() = runTest {
        cut.get("key").first() shouldBe null
        cut.set("key", "value")
        cut.get("key").first() shouldBe "value"
        cacheStore.get("key") shouldBe "value"
    }

    @Test
    fun `write » fill value with update while read is active`() = runTest {
        val startedCollect = MutableStateFlow(false)
        val readResult = async { cut.get("key").onEach { startedCollect.value = true }.filterNotNull().first() }
        startedCollect.first { it }
        cut.update("key") { "value" }
        readResult.await() shouldBe "value"
    }

    @Test
    fun `write » skip cache when no read active`() = runTest {
        cut.set("key", "value")
        cacheStore.persist("key", "otherValue")
        cut.get("key").first() shouldBe "otherValue"
    }

    @Test
    fun `write » infinite cache not enabled » remove from cache when write cache time expired`() = runTest {
        cut.update(
            key = "key",
            updater = { "updated value" },
        )
        delay(1.minutes + 1.milliseconds)
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

    @Test
    fun `write » infinite cache not enabled » reset expireDuration on use`() = runTest {
        cut.update(
            key = "key",
            updater = { "updated value 1" },
        )
        delay(1.minutes + 1.milliseconds)
        cut.invalidate()
        cut.update(
            key = "key",
            updater = {
                it shouldBe "updated value 1"
                "updated value 2"
            },
        )
        delay(1.milliseconds)
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

    @Test
    fun `write » infinite cache enabled » never remove from cache`() = runTest {
        val cut = ObservableCache("", cacheStore, backgroundScope, testClock, expireDuration = Duration.INFINITE)
        cut.update(
            key = "key",
            updater = { "updated value" },
        )
        delay(1.minutes + 1.milliseconds)
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

    @Test
    fun `write » removeFromCacheOnNull enabled » remove from cache when value is null set`() = runTest {
        val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
        val cut =
            ObservableCache("", cacheStore, backgroundScope, testClock, removeFromCacheOnNull = true, values = values)
        cacheStore.persist("key", "a new value")
        cut.get(key = "key").first() shouldBe "a new value"
        values.getAll().size shouldBe 1
        cut.set("key", null)
        values.getAll().size shouldBe 0
    }

    @Test
    fun `write » removeFromCacheOnNull enabled » remove from cache when value is null update`() = runTest {
        val values = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<String?>>>()
        val cut =
            ObservableCache("", cacheStore, backgroundScope, testClock, removeFromCacheOnNull = true, values = values)
        cacheStore.persist("key", "a new value")
        cut.get(key = "key").first() shouldBe "a new value"
        values.getAll().size shouldBe 1
        cut.update("key") { null }
        values.getAll().size shouldBe 0
    }

    @Test
    fun `update cache entry when read during transaction after transaction`() = runTest {
        val transactionManager = TransactionManagerImpl(NoOpRepositoryTransactionManager)
        launch {
            transactionManager.writeTransaction {
                cut.set("key", "value")
                cacheStore.persist("key", null) // simulate that write is only visible after transaction
                delay(100.milliseconds)
            }
        }
        delay(50.milliseconds)
        cut.get("key").first() shouldBe null
        delay(51.milliseconds)
        cut.get("key").first() shouldBe "value"
    }

    @Test
    fun `rollback cache entry when used during transaction and failing`() = runTest {
        val transactionManager = TransactionManagerImpl(NoOpRepositoryTransactionManager)
        cut.update("key1") { "value0" }
        cut.update("key2") { null }
        launch {
            shouldThrow<RuntimeException> {
                transactionManager.writeTransaction {
                    cut.update("key1") { "value1" }
                    cut.set("key1", "value2")
                    cut.set("key2", "value3")
                    cut.update("key2") { "value4" }
                    delay(100.milliseconds)
                    throw CancellationException("sync cancelled")
                }
            }
        }
        delay(50.milliseconds)
        cut.get("key1").first() shouldBe "value2"
        cut.get("key2").first() shouldBe "value4"
        delay(51.milliseconds)
        cut.get("key1").first() shouldBe "value0"
        cut.get("key2").first() shouldBe null
    }

    @Test
    fun `rollback cache entry on error`() = runTest {
        val throwingCacheStore = object : ObservableCacheStore<String, String> {
            override suspend fun get(key: String): String? = "old"

            override suspend fun persist(key: String, value: String?) {
                throw RuntimeException("upsi")
            }

            override suspend fun deleteAll() {}
        }
        val cut = ObservableCache("test", throwingCacheStore, testScope.backgroundScope, testScope.testClock)
        cut.get("key").first() shouldBe "old"
        shouldThrow<RuntimeException> {
            cut.update("key") { "new" }
        }.message shouldBe "upsi"
        cut.get("key").first() shouldBe "old"
    }

    @Test
    fun `not rollback cache entry on cancellation`() = runTest {
        val onPersist = MutableStateFlow(false)
        val throwingCacheStore = object : ObservableCacheStore<String, String> {
            var value: String? = "old"
            override suspend fun get(key: String): String? = value

            override suspend fun persist(key: String, value: String?) {
                onPersist.value = true
                delay(50.milliseconds)
                this.value = value
            }

            override suspend fun deleteAll() {}
        }
        val cut = ObservableCache("test", throwingCacheStore, testScope.backgroundScope, testScope.testClock)
        cut.get("key").first() shouldBe "old"
        val job = async {
            cut.update("key") { "new" }
        }
        onPersist.first { it }
        job.cancel()
        delay(100.milliseconds)
        cut.get("key").first() shouldBe "new"
    }

    @Test
    fun `index » call onPut on cache insert`() = runTest {
        indexedCut.update("key") { "value" }
        indexedCut.index.onPut.value shouldBe "key"
        indexedCut.index.onRemove.value shouldBe null
    }

    @Test
    fun `index » call onSkipPut on cache skip`() = runTest {
        indexedCut.set("key", "value")
        indexedCut.index.onSkipPut.value shouldBe "key"
        indexedCut.index.onPut.value shouldBe null
        indexedCut.index.onRemove.value shouldBe null
    }

    @Test
    fun `index » call not onPut on existing cache value`() = runTest {
        indexedCut.set("key", "value")
        indexedCut.index.onPut.value = null
        indexedCut.set("key", "value")
        indexedCut.index.onPut.value shouldBe null
    }

    @Test
    fun `index » call onRemove on cache remove`() = runTest {
        indexedCut.update("key") { "value" }
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onPut.value shouldBe "key"
        indexedCut.index.onRemove.first() shouldBe ("key" to false)
    }

    @Test
    fun `index » call onRemoveALl on clear`() = runTest {
        indexedCut.set("key", "value")
        indexedCut.clear()
        indexedCut.index.onRemoveAllCalled.value shouldBe true
    }

    @Test
    fun `index » wait for index subsciptions before remove from cache`() = runTest {
        indexedCut.index.subscriptionCount = 1
        indexedCut.update("key") { "value" }
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onRemove.value shouldBe null
        indexedCut.index.subscriptionCount = 0
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onRemove.first() shouldBe ("key" to false)
    }

    @Test
    fun `index » allow remove from cache when index subscriptions gt 0 but value==null`() = runTest {
        indexedCut.index.subscriptionCount = 1
        indexedCut.update("key") { null }
        delay(1.minutes + 1.milliseconds)
        indexedCut.invalidate()
        indexedCut.index.onRemove.first() shouldBe ("key" to true)
    }

    @Test
    fun `index » not remove from cache when alreay null on upate`() = runTest {
        val barrier = CompletableDeferred<Unit>()
        val myFlow = async { indexedCut.get("key").onStart { barrier.complete(Unit) }.drop(1).first() }
        barrier.await()
        indexedCut.set("key", null)
        indexedCut.update("key") { "my elem" }
        myFlow.await() shouldBe "my elem"
    }

    @Test
    fun `write » fill value with set when cache entry not present but subscribed`() = runTest {
        indexedCut.index.subscriptionCount = 1
        indexedCut.set("key", "value")
        indexedCut.get("key").first() shouldBe "value"
        cacheStore.get("key") shouldBe "value"
    }
}

private class TestObservableCacheIndex<T> : ObservableCacheIndex<T> {
    val onPut = MutableStateFlow<T?>(null)
    val onSkipPut = MutableStateFlow<T?>(null)
    val onRemove = MutableStateFlow<Pair<T, Boolean>?>(null)
    val onRemoveAllCalled = MutableStateFlow(false)
    var subscriptionCount = 0

    override suspend fun onPut(key: T) {
        onPut.value = key
    }

    override suspend fun onSkipPut(key: T) {
        onSkipPut.value = key
    }


    override suspend fun onRemove(key: T, stale: Boolean) {
        onRemove.value = key to stale
    }

    override suspend fun onRemoveAll() {
        onRemoveAllCalled.value = true
    }

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic? = null

    override suspend fun getSubscriptionCount(key: T): Int = subscriptionCount
}

private class TestIndexedObservableCache(
    name: String,
    store: InMemoryObservableCacheStore<String, String>,
    cacheScope: CoroutineScope,
    clock: Clock,
    expireDuration: Duration = 1.minutes,
) : ObservableCache<String, String, InMemoryObservableCacheStore<String, String>>(
    name, store, cacheScope, clock, expireDuration
) {
    val index = TestObservableCacheIndex<String>()

    init {
        addIndex(index)
    }
}