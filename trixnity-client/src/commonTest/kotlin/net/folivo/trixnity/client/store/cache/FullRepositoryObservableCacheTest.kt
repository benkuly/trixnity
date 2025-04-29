package net.folivo.trixnity.client.store.cache

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.store.repository.InMemoryFullRepository
import net.folivo.trixnity.client.store.repository.NoOpRepositoryTransactionManager
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test

class FullRepositoryObservableCacheTest : TrixnityBaseTest() {
    data class Entry(
        val key: String,
        val value: String,
    )

    private val repository = object : InMemoryFullRepository<String, Entry>() {
        override fun serializeKey(key: String): String = key
    }
    private val cut = FullRepositoryObservableCache(
        repository,
        NoOpRepositoryTransactionManager,
        testScope.backgroundScope,
        testScope.testClock,
    ) { it.key }

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
}