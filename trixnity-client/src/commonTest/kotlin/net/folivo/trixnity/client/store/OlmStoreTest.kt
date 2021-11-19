package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.repository.OlmAccountRepository

class OlmStoreTest : ShouldSpec({
    val olmAccountRepository = mockk<OlmAccountRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: OlmStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = OlmStore(olmAccountRepository, mockk(), mockk(), mockk(), mockk(), storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }
    context(OlmStore::init.name) {
        should("load values from database") {
            coEvery { olmAccountRepository.get(1) } returns "olm_account"

            cut.init()

            cut.account.value shouldBe "olm_account"
        }
        should("start job, which saves changes to database") {
            coEvery { olmAccountRepository.get(1) } returns null

            cut.init()

            cut.account.value = "olm_account"
            coVerify(timeout = 5_000) {
                olmAccountRepository.save(1, "olm_account")
            }
        }
    }
})