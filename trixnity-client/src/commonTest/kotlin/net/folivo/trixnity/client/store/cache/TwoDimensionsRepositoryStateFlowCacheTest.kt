package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.TwoDimensionsStoreRepository

class TwoDimensionsRepositoryStateFlowCacheTest : ShouldSpec({
    val repository = mockk<TwoDimensionsStoreRepository<String, String, String>>(relaxUnitFun = true)
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: TwoDimensionsRepositoryStateFlowCache<String, String, String, TwoDimensionsStoreRepository<String, String, String>>
    val transactionWasCalled = MutableStateFlow(false)
    val rtm = object : RepositoryTransactionManager {
        override suspend fun <T> transaction(block: suspend () -> T): T {
            transactionWasCalled.value = true
            return block()
        }
    }

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
        transactionWasCalled.value = false
        cut = TwoDimensionsRepositoryStateFlowCache(cacheScope, repository, rtm)
    }
    afterTest {
        clearAllMocks()
        cacheScope.cancel()
        cut = mockk() // just in case we forgot to init a new cut for a test
    }

    should("handle get after getBySecondKey") {
        coEvery { repository.get("firstKey") } returns mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        coEvery { repository.getBySecondKey("firstKey", "secondKey1") } returns "value1"
        cut.getBySecondKey("firstKey", "secondKey1") shouldBe "value1"
        cut.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        cut.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        coVerify(exactly = 1) { repository.get("firstKey") }
    }
    context("updateBySecondKey") {
        should("always save") {
            coEvery { repository.get("firstKey") } returns mapOf("secondKey" to "value")
            cut.updateBySecondKey("firstKey", "secondKey") { "value" }
            coVerify { repository.saveBySecondKey("firstKey", "secondKey", "value") }
            cut.get("firstKey") shouldBe mapOf("secondKey" to "value")
        }
        should("always delete") {
            coEvery { repository.get("firstKey") } returns null
            cut.updateBySecondKey("firstKey", "secondKey") { null }
            coVerify { repository.deleteBySecondKey("firstKey", "secondKey") }
            cut.get("firstKey") shouldBe null
        }
        should("update existing cache value") {
            coEvery { repository.get("firstKey") } returns mapOf("secondKey1" to "value1")
            cut.updateBySecondKey("firstKey", "secondKey1") { "value1" }
            cut.get("firstKey") shouldBe mapOf("secondKey1" to "value1")
            cut.updateBySecondKey("firstKey", "secondKey2") { "value2" }
            cut.get("firstKey") shouldBe mapOf("secondKey1" to "value1", "secondKey2" to "value2")
        }
    }
    context("getBySecondKey") {
        should("load from database, when not exists in cache") {
            coEvery { repository.get("firstKey") } returns mapOf()
            coEvery { repository.getBySecondKey("firstKey", "secondKey2") } returns "value2"
            cut.update("firstKey") {
                mapOf("secondKey1" to "value1")
            }
            cut.getBySecondKey("firstKey", "secondKey2") shouldBe "value2"
            cut.getBySecondKey("firstKey", "secondKey2") shouldBe "value2"
            coVerify(exactly = 1) { repository.getBySecondKey("firstKey", "secondKey2") }
        }
        should("prefer cache") {
            coEvery { repository.get("firstKey") } returns mapOf()
            cut.update("firstKey") {
                mapOf("secondKey1" to "value1")
            }
            cut.getBySecondKey("firstKey", "secondKey1") shouldBe "value1"
            coVerify(exactly = 0) { repository.getBySecondKey(any(), any()) }
        }
    }
})