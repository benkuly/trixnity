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
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.MatrixId.UserId

class AccountStoreTest : ShouldSpec({
    val repository = mockk<AccountRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: AccountStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = AccountStore(repository, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(AccountStore::init.name) {
        should("load values from database") {
            coEvery { repository.get(1) } returns Account(
                UserId("user", "server"),
                "device",
                "access_token",
                "sync_token",
                "filter_id"
            )

            cut.init()

            cut.userId.value shouldBe UserId("user", "server")
            cut.deviceId.value shouldBe "device"
            cut.accessToken.value shouldBe "access_token"
            cut.syncBatchToken.value shouldBe "sync_token"
            cut.filterId.value shouldBe "filter_id"
        }
        should("start job, which saves changes to database") {
            coEvery { repository.get(1) } returns null

            cut.init()

            cut.userId.value = UserId("user", "server")
            coVerify(timeout = 2_000) {
                repository.save(
                    1, Account(
                        UserId("user", "server"),
                        null,
                        null,
                        null,
                        null
                    )
                )
            }
        }
    }
})