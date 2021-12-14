package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.Key

class KeysStore(
    private val outdatedDeviceKeysRepository: OutdatedDeviceKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    crossSigningKeysRepository: CrossSigningKeysRepository,
    keyVerificationStateRepository: KeyVerificationStateRepository,
    private val storeScope: CoroutineScope
) {
    val outdatedKeys = MutableStateFlow<Set<UserId>>(setOf())
    private val deviceKeysCache = StateFlowCache(storeScope, deviceKeysRepository)
    private val crossSigningKeysCache = StateFlowCache(storeScope, crossSigningKeysRepository)
    private val keyVerificationStateCache = StateFlowCache(storeScope, keyVerificationStateRepository)

    suspend fun init() {
        outdatedKeys.value = outdatedDeviceKeysRepository.get(1) ?: setOf()
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            outdatedKeys.collect { outdatedDeviceKeysRepository.save(1, it) }
        }
    }

    suspend fun getDeviceKeys(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<Map<String, StoredDeviceKeys>?> = deviceKeysCache.get(userId, scope)

    suspend fun getDeviceKeys(
        userId: UserId,
    ): Map<String, StoredDeviceKeys>? = deviceKeysCache.get(userId)

    suspend fun updateDeviceKeys(
        userId: UserId,
        updater: suspend (Map<String, StoredDeviceKeys>?) -> Map<String, StoredDeviceKeys>?
    ) = deviceKeysCache.update(userId, updater)

    suspend fun getCrossSigningKeys(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<Set<StoredCrossSigningKey>?> = crossSigningKeysCache.get(userId, scope)

    suspend fun getCrossSigningKeys(
        userId: UserId,
    ): Set<StoredCrossSigningKey>? = crossSigningKeysCache.get(userId)

    suspend fun updateCrossSigningKeys(
        userId: UserId,
        updater: suspend (Set<StoredCrossSigningKey>?) -> Set<StoredCrossSigningKey>?
    ) = crossSigningKeysCache.update(userId, updater)

    suspend fun getKeyVerificationState(
        key: Key,
        userId: UserId,
        deviceId: String? = null
    ): KeyVerificationState? {
        val keyId = key.keyId
        return keyId?.let {
            keyVerificationStateCache.get(
                VerifiedKeysRepositoryKey(
                    keyId = it,
                    keyAlgorithm = key.algorithm,
                    userId = userId,
                    deviceId = deviceId
                )
            )?.let { state ->
                if (state.keyValue == key.value) state
                else KeyVerificationState.Blocked(state.keyValue)
            }
        }
    }

    suspend fun saveKeyVerificationState(
        key: Key,
        userId: UserId,
        deviceId: String? = null,
        state: KeyVerificationState
    ) {
        val keyId = key.keyId
        requireNotNull(keyId)
        keyVerificationStateCache.update(
            VerifiedKeysRepositoryKey(keyId = keyId, keyAlgorithm = key.algorithm, userId = userId, deviceId = deviceId)
        ) { state }
    }
}