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
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.core.model.UserId

class DeviceKeysStoreTest : ShouldSpec({
    val outdatedKeysRepository = mockk<OutdatedKeysRepository>(relaxUnitFun = true)
    val deviceKeysRepository = mockk<DeviceKeysRepository>(relaxUnitFun = true)
    val crossSigningKeysRepository = mockk<CrossSigningKeysRepository>(relaxUnitFun = true)
    val keyVerificationStateRepository = mockk<KeyVerificationStateRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: KeysStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = KeysStore(
            outdatedKeysRepository,
            deviceKeysRepository,
            crossSigningKeysRepository,
            keyVerificationStateRepository,
            NoopRepositoryTransactionManager,
            storeScope
        )
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(KeysStore::init.name) {
        should("load values from database") {
            coEvery { outdatedKeysRepository.get(1) } returns setOf(
                UserId("alice", "server"), UserId("bob", "server")
            )

            cut.init()

            cut.outdatedKeys.value shouldBe setOf(UserId("alice", "server"), UserId("bob", "server"))
        }
        should("start job, which saves changes to database") {
            coEvery { outdatedKeysRepository.get(1) } returns null

            cut.init()

            cut.outdatedKeys.value = setOf(UserId("alice", "server"), UserId("bob", "server"))

            coVerify(timeout = 1_000) {
                outdatedKeysRepository.save(
                    1, setOf(UserId("alice", "server"), UserId("bob", "server"))
                )
            }
        }
    }
})