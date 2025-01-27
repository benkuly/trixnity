package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.InMemoryAccountRepository
import net.folivo.trixnity.core.model.UserId

class AccountStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var repository: AccountRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: AccountStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        repository = InMemoryAccountRepository()
        cut = AccountStore(
            repository,
            RepositoryTransactionManagerMock(),
            ObservableCacheStatisticCollector(),
            storeScope,
            Clock.System,
        )
    }
    afterTest {
        storeScope.cancel()
    }

    context(AccountStore::init.name) {
        should("load values from database") {
            repository.save(
                1, Account(
                    olmPickleKey = "",
                    baseUrl = "http://localhost",
                    userId = UserId("user", "server"),
                    deviceId = "device",
                    accessToken = "access_token",
                    refreshToken = "refresh_token",
                    syncBatchToken = "sync_token",
                    filterId = "filter_id",
                    backgroundFilterId = "background_filter_id",
                    displayName = "display_name",
                    avatarUrl = "mxc://localhost/123456",
                )
            )

            cut.init(this)

            cut.getAccount().shouldNotBeNull().run {
                olmPickleKey shouldBe ""
                baseUrl shouldBe "http://localhost"
                userId shouldBe UserId("user", "server")
                deviceId shouldBe "device"
                accessToken shouldBe "access_token"
                refreshToken shouldBe "refresh_token"
                syncBatchToken shouldBe "sync_token"
                filterId shouldBe "filter_id"
                displayName shouldBe "display_name"
                avatarUrl shouldBe "mxc://localhost/123456"
            }
        }
    }
})