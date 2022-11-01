package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.repository.InMemoryTwoDimensionsStoreRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.TwoDimensionsStoreRepository

class TwoDimensionsRepositoryStateFlowCacheTest : ShouldSpec({
    timeout = 60_000
    lateinit var repository: TwoDimensionsStoreRepository<String, String, String>
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: TwoDimensionsRepositoryStateFlowCache<String, String, String, TwoDimensionsStoreRepository<String, String, String>>
    val transactionWasCalled = MutableStateFlow(false)
    val rtm = object : RepositoryTransactionManager {
        override suspend fun <T> readTransaction(block: suspend () -> T): T {
            transactionWasCalled.value = true
            return block()
        }

        override suspend fun <T> writeTransaction(block: suspend () -> T): T {
            transactionWasCalled.value = true
            return block()
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        transactionWasCalled.value = false
        repository = InMemoryTwoDimensionsStoreRepository()
        cut = TwoDimensionsRepositoryStateFlowCache(cacheScope, repository, rtm)
    }
    afterTest {
        cacheScope.cancel()
    }

    should("handle get after getBySecondKey") {
        repository.save("firstKey", mapOf("secondKey1" to "value1", "secondKey2" to "value2"))
        cut.getBySecondKey("firstKey", "secondKey1").first() shouldBe "value1"
        cut.get("firstKey").first() shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        repository.save("firstKey", mapOf())
        cut.get("firstKey").first() shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
    }
    context("updateBySecondKey") {
        should("always save") {
            repository.save("firstKey", mapOf("secondKey1" to "old"))
            cut.updateBySecondKey("firstKey", "secondKey1") {
                it shouldBe "old"
                "value1"
            }
            // we overwrite the repository to check, that only secondKey1 is updated
            repository.save("firstKey", mapOf("secondKey1" to "old", "secondKey2" to "old"))
            cut.updateBySecondKey("firstKey", "secondKey1") {
                it shouldBe "value1"
                "value2"
            }
            cut.get("firstKey").first() shouldBe mapOf("secondKey1" to "value2", "secondKey2" to "old")
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value2", "secondKey2" to "old")
        }
        should("always delete") {
            repository.save("firstKey", mapOf())
            cut.updateBySecondKey("firstKey", "secondKey1") {
                it shouldBe null
                null
            }
            repository.save("firstKey", mapOf("secondKey1" to "old"))
            cut.updateBySecondKey("firstKey", "secondKey1") {
                it shouldBe "old"
                null
            }
            repository.get("firstKey") shouldBe mapOf()
            cut.get("firstKey").first() shouldBe mapOf()
        }
        should("update existing cache value") {
            repository.save("firstKey", mapOf("secondKey1" to "old"))
            cut.updateBySecondKey("firstKey", "secondKey1") {
                it shouldBe "old"
                "value1"
            }
            cut.get("firstKey").first() shouldBe mapOf("secondKey1" to "value1")
            cut.updateBySecondKey("firstKey", "secondKey2") { "value2" }
            cut.get("firstKey").first() shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
            repository.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        }
    }
    context("getBySecondKey") {
        should("load from database, when not exists in cache") {
            repository.save("firstKey", mapOf("secondKey1" to "old1", "secondKey2" to "old2"))
            cut.getBySecondKey("firstKey", "secondKey1").first() shouldBe "old1"
            repository.save("firstKey", mapOf("secondKey1" to "new1", "secondKey2" to "new2"))
            cut.getBySecondKey("firstKey", "secondKey1").first() shouldBe "old1"
            cut.getBySecondKey("firstKey", "secondKey2").first() shouldBe "new2"
        }
        should("prefer cache") {
            cut.update("firstKey") {
                mapOf("secondKey1" to "value1")
            }
            repository.save("firstKey", mapOf())
            cut.getBySecondKey("firstKey", "secondKey1").first() shouldBe "value1"
        }
    }
})