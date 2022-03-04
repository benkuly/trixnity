package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId

class AccountStoreTest : ShouldSpec({
    val repository = mockk<AccountRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: AccountStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = AccountStore(repository, NoopRepositoryTransactionManager, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(AccountStore::init.name) {
        should("load values from database") {
            coEvery { repository.get(1) } returns Account(
                "",
                "http://localhost",
                UserId("user", "server"),
                "device",
                "access_token",
                "sync_token",
                "filter_id",
                "background_filter_id",
                "display_name",
                "mxc://localhost/123456",
            )

            cut.init()

            cut.olmPickleKey.value shouldBe ""
            cut.baseUrl.value shouldBe Url("http://localhost")
            cut.userId.value shouldBe UserId("user", "server")
            cut.deviceId.value shouldBe "device"
            cut.accessToken.value shouldBe "access_token"
            cut.syncBatchToken.value shouldBe "sync_token"
            cut.filterId.value shouldBe "filter_id"
            cut.displayName.value shouldBe "display_name"
            cut.avatarUrl.value shouldBe "mxc://localhost/123456"
        }
        should("start job, which saves changes to database") {
            coEvery { repository.get(1) } returns null

            cut.init()

            cut.userId.value = UserId("user", "server")
            coVerify(timeout = 5_000) {
                repository.save(
                    1, Account(
                        null,
                        null,
                        UserId("user", "server"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                )
            }
        }
    }
})