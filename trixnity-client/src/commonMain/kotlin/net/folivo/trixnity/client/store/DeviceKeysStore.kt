package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.client.store.repository.OutdatedDeviceKeysRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

class DeviceKeysStore(
    private val outdatedDeviceKeysRepository: OutdatedDeviceKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    verifiedKeysRepository: VerifiedKeysRepository,
    private val storeScope: CoroutineScope
) {
    val outdatedKeys = MutableStateFlow<Set<UserId>>(setOf())
    private val deviceKeysCache = StateFlowCache(storeScope, deviceKeysRepository)
    private val verifiedKeysCache = StateFlowCache(storeScope, verifiedKeysRepository)

    suspend fun init() {
        outdatedKeys.value = outdatedDeviceKeysRepository.get(1) ?: setOf()
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            outdatedKeys.collect { outdatedDeviceKeysRepository.save(1, it) }
        }
    }

    suspend fun get(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<Map<String, DeviceKeys>?> = deviceKeysCache.get(userId, scope)

    suspend fun get(
        userId: UserId,
    ): Map<String, DeviceKeys>? = deviceKeysCache.get(userId)

    suspend fun update(
        userId: UserId,
        updater: suspend (oldRoom: Map<String, DeviceKeys>?) -> Map<String, DeviceKeys>?
    ) = deviceKeysCache.update(userId, updater)

    suspend fun isVerified(
        key: Key,
        userId: UserId,
        deviceId: String? = null
    ): Boolean {
        val keyId = key.keyId
        return if (keyId != null)
            verifiedKeysCache.get(
                VerifiedKeysRepositoryKey(
                    keyId = keyId,
                    keyAlgorithm = key.algorithm,
                    userId = userId,
                    deviceId = deviceId
                )
            ) == key.value
        else false
    }

    suspend fun markVerified(key: Key, userId: UserId, deviceId: String? = null) {
        val keyId = key.keyId
        requireNotNull(keyId)
        verifiedKeysCache.update(
            VerifiedKeysRepositoryKey(keyId = keyId, keyAlgorithm = key.algorithm, userId = userId, deviceId = deviceId)
        ) { key.value }
    }

    suspend fun unmarkVerified(keyId: String, keyAlgorithm: KeyAlgorithm, userId: UserId, deviceId: String? = null) {
        verifiedKeysCache.update(
            VerifiedKeysRepositoryKey(keyId = keyId, keyAlgorithm = keyAlgorithm, userId = userId, deviceId = deviceId)
        ) { null }
    }

}