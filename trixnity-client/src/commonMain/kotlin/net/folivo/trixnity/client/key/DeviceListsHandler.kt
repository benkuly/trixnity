package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.isTracked
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler

private val log = KotlinLogging.logger {}

class DeviceListsHandler(
    private val api: IMatrixClientServerApiClient,
    private val keyStore: KeyStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeDeviceLists(::handleDeviceLists)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeDeviceLists(::handleDeviceLists)
        }
    }

    internal suspend fun handleDeviceLists(deviceList: Sync.Response.DeviceLists?) {
        if (deviceList == null) return
        log.debug { "set outdated device keys or remove old device keys" }
        deviceList.changed?.let { userIds ->
            keyStore.outdatedKeys.update { oldUserIds ->
                oldUserIds + userIds.filter { keyStore.isTracked(it) }
            }
        }
        deviceList.left?.forEach { userId ->
            keyStore.outdatedKeys.update { it - userId }
            keyStore.updateDeviceKeys(userId) { null }
            keyStore.updateCrossSigningKeys(userId) { null }
        }
    }
}