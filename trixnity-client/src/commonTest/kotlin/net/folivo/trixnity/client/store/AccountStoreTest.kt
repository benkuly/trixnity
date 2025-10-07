package net.folivo.trixnity.client.store

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.InMemoryAccountRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test

class AccountStoreTest : TrixnityBaseTest() {

    private val repository = InMemoryAccountRepository() as AccountRepository
    private val cut = AccountStore(
        repository,
        RepositoryTransactionManagerMock(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    )

    @Test
    fun `init » load values from database`() = runTest {
        repository.save(
            1, Account(
                olmPickleKey = null,
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
            olmPickleKey shouldBe null
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