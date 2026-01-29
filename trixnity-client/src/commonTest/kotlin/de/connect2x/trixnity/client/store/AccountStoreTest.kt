package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.mocks.RepositoryTransactionManagerMock
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.AccountRepository
import de.connect2x.trixnity.client.store.repository.InMemoryAccountRepository
import de.connect2x.trixnity.clientserverapi.model.user.Profile
import de.connect2x.trixnity.clientserverapi.model.user.ProfileField
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
                filter = Account.Filter(
                    syncFilterId = "filter_id",
                    syncOnceFilterId = "background_filter_id",
                    eventTypesHash = "someHash",
                ),
                profile = Profile(
                    ProfileField.DisplayName("display name"),
                    ProfileField.AvatarUrl("mxc://localhost/123456")
                ),
            )
        )

        cut.init(this)

        cut.getAccount().shouldNotBeNull().run {
            olmPickleKey shouldBe null
            @Suppress("DEPRECATION")
            baseUrl shouldBe "http://localhost"
            userId shouldBe UserId("user", "server")
            deviceId shouldBe "device"
            @Suppress("DEPRECATION")
            accessToken shouldBe "access_token"
            @Suppress("DEPRECATION")
            refreshToken shouldBe "refresh_token"
            syncBatchToken shouldBe "sync_token"
            filter shouldBe Account.Filter(
                syncFilterId = "filter_id",
                syncOnceFilterId = "background_filter_id",
                eventTypesHash = "someHash",
            )
            profile shouldBe Profile(
                ProfileField.DisplayName("display name"),
                ProfileField.AvatarUrl("mxc://localhost/123456")
            )
        }
    }
}