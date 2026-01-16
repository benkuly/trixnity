package de.connect2x.trixnity.client.store.cache

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import de.connect2x.trixnity.client.flatten
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.store.repository.InMemoryFullRepository
import de.connect2x.trixnity.client.store.repository.NoOpRepositoryTransactionManager
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class FullRepositoryObservableCacheTest : TrixnityBaseTest() {
    data class Entry(
        val key: String,
        val value: String,
    )

    private class TestInMemoryFullRepository() : InMemoryFullRepository<String, Entry>() {
        val continueGetAll = MutableStateFlow(true)
        override suspend fun getAll(): List<Entry> {
            val result = super.getAll()
            continueGetAll.first { it }
            return result
        }

        override fun serializeKey(key: String): String = key
    }

    private val repository = TestInMemoryFullRepository()
        .also {
            scheduleSetup { it.deleteAll() }
        }
    private val cut = FullRepositoryObservableCache(
        repository = repository,
        tm = NoOpRepositoryTransactionManager,
        cacheScope = testScope.backgroundScope,
        clock = testScope.testClock,
        expireDuration = 1.minutes,
    ) { it.key }.also {
        scheduleSetup { it.clear() }
    }

    @Test
    fun `readAll » read all values`() = runTest {
        repository.save("key1", Entry("key1", "old1"))
        repository.save("key2", Entry("key2", "old2"))
        cut.readAll().flatten().map { it.mapValues { it.value?.value } }.first() shouldBe
                mapOf("key1" to "old1", "key2" to "old2")
    }

    @Test
    fun `readAll » remove from cache when stale`() = runTest {
        repository.save("key1", Entry("key1", "old1"))
        repository.save("key2", Entry("key2", "old2"))
        val all = cut.readAll().flatten().map { it.keys }.stateIn(backgroundScope)
        all.value shouldBe setOf("key1", "key2")
        cut.update("key1") { null }
        all.first { it == setOf("key2") }
    }

    @Test
    fun `readAll » don't forget fully loaded state`() = runTest {
        repository.save("key1", Entry("key1", "old1"))
        repository.save("key2", Entry("key2", "old2"))
        cut.readAll().first().keys shouldBe setOf("key1", "key2")
        cut.set("key3", Entry("key3", "old3"))
        cut.update("key1") { null }
        cut.invalidate()
        cut.readAll().first().keys shouldBe setOf("key2", "key3")
    }

    @Test
    fun `readAll » don't invalidate when subscribed`() = runTest {
        val observeK1 = backgroundScope.async { cut.get("k1").collect() }
        delay(10.milliseconds)
        cut.set("k1", Entry("k1", "v1"))
        cut.set("k2", Entry("k2", "v2"))
        observeK1.cancel()

        repository.continueGetAll.value = false // this forces a delay in the repository (so it will return k1, k2)
        val result = async { cut.readAll().flattenValues().first().toSet() }

        delay(2.minutes) // invalidate cache (removes k1,k2)

        cut.set("k3", Entry("k3", "v3")) // should not skip cache

        repository.continueGetAll.value = true
        result.await() shouldBe setOf(
            Entry("k1", "v1"),
            Entry("k2", "v2"),
            Entry("k3", "v3")
        )
    }
}