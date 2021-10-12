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
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.client.store.repository.OutdatedDeviceKeysRepository
import net.folivo.trixnity.core.model.MatrixId.UserId

class DeviceKeysStoreTest : ShouldSpec({
    val outdatedDeviceKeysRepository = mockk<OutdatedDeviceKeysRepository>(relaxUnitFun = true)
    val deviceKeysRepository = mockk<DeviceKeysRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: DeviceKeysStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = DeviceKeysStore(outdatedDeviceKeysRepository, deviceKeysRepository, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    context(DeviceKeysStore::init.name) {
        should("load values from database") {
            coEvery { outdatedDeviceKeysRepository.get(1) } returns setOf(
                UserId("alice", "server"), UserId("bob", "server")
            )

            cut.init()

            cut.outdatedKeys.value shouldBe setOf(
                UserId("alice", "server"), UserId("bob", "server")
            )
        }
        should("start job, which saves changes to database") {
            coEvery { outdatedDeviceKeysRepository.get(1) } returns null

            cut.init()

            cut.outdatedKeys.value = setOf(
                UserId("alice", "server"), UserId("bob", "server")
            )
            coVerify {
                outdatedDeviceKeysRepository.save(
                    1, setOf(
                        UserId("alice", "server"), UserId("bob", "server")
                    )
                )
            }
        }
    }
})