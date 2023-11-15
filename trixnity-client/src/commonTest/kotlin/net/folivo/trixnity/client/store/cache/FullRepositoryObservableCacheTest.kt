package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.store.repository.FullRepository
import net.folivo.trixnity.client.store.repository.InMemoryFullRepository
import net.folivo.trixnity.client.store.repository.NoOpRepositoryTransactionManager

class FullRepositoryObservableCacheTest : ShouldSpec({
    timeout = 5_000
    data class Entry(
        val key: String,
        val value: String,
    )

    lateinit var repository: FullRepository<String, Entry>
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: FullRepositoryObservableCache<String, Entry>

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        repository = object : InMemoryFullRepository<String, Entry>() {
            override fun serializeKey(key: String): String = key
        }
        cut = FullRepositoryObservableCache(repository, NoOpRepositoryTransactionManager, cacheScope) { it.key }
    }
    afterTest {
        cacheScope.cancel()
    }

    context("readAll") {
        should("read all values") {
            repository.save("key1", Entry("key1", "old1"))
            repository.save("key2", Entry("key2", "old2"))
            cut.readAll().flatten().map { it.mapValues { it.value?.value } }.first() shouldBe
                    mapOf("key1" to "old1", "key2" to "old2")
        }
        should("remove from cache when stale") {
            val readScope = CoroutineScope(Dispatchers.Default)
            repository.save("key1", Entry("key1", "old1"))
            repository.save("key2", Entry("key2", "old2"))
            val all = cut.readAll().flatten().map { it.keys }.stateIn(readScope)
            all.value shouldBe setOf("key1", "key2")
            cut.write("key1") { null }
            all.first { it == setOf("key2") }
        }
    }
})