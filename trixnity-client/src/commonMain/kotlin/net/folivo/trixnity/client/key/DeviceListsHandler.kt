package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.isTracked
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class DeviceListsHandler(
    private val api: MatrixClientServerApiClient,
    private val keyStore: KeyStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.DEVICE_LISTS) { handleDeviceLists(it.syncResponse.deviceLists) }
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun handleDeviceLists(deviceList: Sync.Response.DeviceLists?) = tm.writeTransaction {
        if (deviceList != null) {
            log.debug { "set outdated device keys or remove old device keys" }
            deviceList.changed?.let { userIds ->
                keyStore.updateOutdatedKeys { oldUserIds ->
                    oldUserIds + userIds.filter { keyStore.isTracked(it) }
                }
            }
            deviceList.left?.forEach { userId ->
                keyStore.updateOutdatedKeys { it - userId }
                keyStore.deleteDeviceKeys(userId)
                keyStore.deleteCrossSigningKeys(userId)
            }
        }
    }
}