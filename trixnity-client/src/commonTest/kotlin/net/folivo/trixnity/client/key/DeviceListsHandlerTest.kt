package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class DeviceListsHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    val json = createMatrixEventJson()

    lateinit var cut: DeviceListsHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        cut = DeviceListsHandler(
            mockMatrixClientServerApiClient(json).first, keyStore, RepositoryTransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
    }

    context(DeviceListsHandler::handleDeviceLists.name) {
        context("device key is tracked") {
            should("add changed devices to outdated keys") {
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.updateDeviceKeys(bob) { mapOf() }
                cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)))
                keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice, bob)
            }
            should("remove key when user left") {
                keyStore.updateOutdatedKeys { setOf(alice, bob) }
                keyStore.updateDeviceKeys(alice) { mapOf() }
                cut.handleDeviceLists(Sync.Response.DeviceLists(left = setOf(alice)))
                keyStore.getDeviceKeys(alice).first() should beNull()
                keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(bob)
            }
        }
        context("device key is not tracked") {
            should("not add changed devices to outdated keys") {
                keyStore.updateOutdatedKeys { setOf(alice) }
                cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)))
                keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice)
            }
        }
    }
}