package net.folivo.trixnity.client.store

import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.InMemoryAccountRepository
import net.folivo.trixnity.client.store.repository.NoOpRepositoryTransactionManager
import net.folivo.trixnity.core.model.UserId
import kotlin.time.Duration.Companion.seconds

class AccountStoreTest : ShouldSpec({
    timeout = 60_000
    lateinit var repository: AccountRepository
    lateinit var storeScope: CoroutineScope
    lateinit var cut: AccountStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        repository = InMemoryAccountRepository()
        cut = AccountStore(repository, NoOpRepositoryTransactionManager, storeScope)
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
            cut.init()

            cut.userId.value = UserId("user", "server")
            eventually(5.seconds) {
                repository.get(1) shouldBe Account(
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
            }
        }
    }
})