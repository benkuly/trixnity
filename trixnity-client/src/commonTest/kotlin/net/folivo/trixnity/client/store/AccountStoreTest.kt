package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
            storeScope
        )
    }
    afterTest {
        storeScope.cancel()
    }

    context(AccountStore::init.name) {
        should("load values from database") {
            repository.save(
                1, Account(
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
            )

            cut.init()

            cut.getAccount().shouldNotBeNull().run {
                olmPickleKey shouldBe ""
                baseUrl shouldBe "http://localhost"
                userId shouldBe UserId("user", "server")
                deviceId shouldBe "device"
                accessToken shouldBe "access_token"
                syncBatchToken shouldBe "sync_token"
                filterId shouldBe "filter_id"
                displayName shouldBe "display_name"
                avatarUrl shouldBe "mxc://localhost/123456"
            }
        }
    }
})