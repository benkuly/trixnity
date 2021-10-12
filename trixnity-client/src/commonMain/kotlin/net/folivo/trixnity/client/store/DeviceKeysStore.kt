package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.client.store.repository.OutdatedDeviceKeysRepository
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys

class DeviceKeysStore(
    private val outdatedDeviceKeysRepository: OutdatedDeviceKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    private val storeScope: CoroutineScope
) {
    val outdatedKeys = MutableStateFlow<Set<UserId>>(setOf())
    private val deviceKeysCache = StateFlowCache(storeScope, deviceKeysRepository)

    suspend fun init() {
        outdatedKeys.value = outdatedDeviceKeysRepository.get(1) ?: setOf()
        storeScope.launch {
            outdatedKeys.collect { outdatedDeviceKeysRepository.save(1, it) }
        }
    }

    suspend fun get(
        userId: UserId,
        scope: CoroutineScope? = null
    ): StateFlow<Map<String, DeviceKeys>?> = deviceKeysCache.get(userId, scope)

    suspend fun update(
        userId: UserId,
        updater: suspend (oldRoom: Map<String, DeviceKeys>?) -> Map<String, DeviceKeys>?
    ) = deviceKeysCache.update(userId, updater)
}