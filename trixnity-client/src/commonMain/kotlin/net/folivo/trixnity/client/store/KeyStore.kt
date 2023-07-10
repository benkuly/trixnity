package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.FullRepositoryCoroutineCache
import net.folivo.trixnity.client.store.cache.MinimalRepositoryCoroutineCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.SecretType
import kotlin.time.Duration

class KeyStore(
    private val outdatedKeysRepository: OutdatedKeysRepository,
    private val deviceKeysRepository: DeviceKeysRepository,
    private val crossSigningKeysRepository: CrossSigningKeysRepository,
    private val keyVerificationStateRepository: KeyVerificationStateRepository,
    private val keyChainLinkRepository: KeyChainLinkRepository,
    private val secretsRepository: SecretsRepository,
    private val secretKeyRequestRepository: SecretKeyRequestRepository,
    private val roomKeyRequestRepository: RoomKeyRequestRepository,
    private val tm: TransactionManager,
    config: MatrixClientConfiguration,
    private val storeScope: CoroutineScope
) : Store {
    private val _outdatedKeys = MutableStateFlow<Set<UserId>>(setOf())
    val outdatedKeys = _outdatedKeys.asStateFlow()
    val secrets = MutableStateFlow<Map<SecretType, StoredSecret>>(mapOf())
    private val deviceKeysCache =
        MinimalRepositoryCoroutineCache(
            repository = deviceKeysRepository,
            tm = tm,
            cacheScope = storeScope,
            expireDuration = config.cacheExpireDurations.deviceKeys
        )
    private val crossSigningKeysCache = MinimalRepositoryCoroutineCache(
        repository = crossSigningKeysRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = config.cacheExpireDurations.crossSigningKeys
    )
    private val keyVerificationStateCache = MinimalRepositoryCoroutineCache(
        repository = keyVerificationStateRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = config.cacheExpireDurations.keyVerificationState
    )
    private val secretKeyRequestCache = FullRepositoryCoroutineCache(
        repository = secretKeyRequestRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = Duration.INFINITE
    ) { it.content.requestId }
    private val roomKeyRequestCache = FullRepositoryCoroutineCache(
        repository = roomKeyRequestRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = Duration.INFINITE
    ) { it.content.requestId }

    override suspend fun init() {
        _outdatedKeys.value = tm.readOperation { outdatedKeysRepository.get(1) ?: setOf() }
        secrets.value = tm.readOperation { secretsRepository.get(1) ?: mapOf() }
        storeScope.launch(start = UNDISPATCHED) {
            secrets.collect {
                tm.writeOperationAsync(secretsRepository.serializeKey(1)) {
                    secretsRepository.save(1, it)
                }
            }
        }
        secretKeyRequestCache.fillWithValuesFromRepository()
        roomKeyRequestCache.fillWithValuesFromRepository()
    }

    override suspend fun clearCache() {
        tm.writeOperation {
            outdatedKeysRepository.deleteAll()
            keyChainLinkRepository.deleteAll()
            outdatedKeysRepository.deleteAll()
        }
        deviceKeysCache.deleteAll()
        crossSigningKeysCache.deleteAll()
        secretKeyRequestCache.deleteAll()
        roomKeyRequestCache.deleteAll()
    }

    override suspend fun deleteAll() {
        clearCache()
        keyVerificationStateCache.deleteAll()
    }

    suspend fun updateOutdatedKeys(updater: suspend (Set<UserId>) -> Set<UserId>) {
        val newValue = _outdatedKeys.updateAndGet { updater(it) }
        tm.writeOperationAsync(outdatedKeysRepository.serializeKey(1)) {
            outdatedKeysRepository.save(1, newValue)
        }
    }

    fun getDeviceKeys(
        userId: UserId,
    ): Flow<Map<String, StoredDeviceKeys>?> = deviceKeysCache.read(userId)

    suspend fun updateDeviceKeys(
        userId: UserId,
        updater: suspend (Map<String, StoredDeviceKeys>?) -> Map<String, StoredDeviceKeys>?
    ) = deviceKeysCache.write(userId, updater = updater)

    suspend fun saveDeviceKeys(
        userId: UserId,
        deviceKeys: Map<String, StoredDeviceKeys>
    ) = deviceKeysCache.write(userId, deviceKeys)

    suspend fun deleteDeviceKeys(userId: UserId) = deviceKeysCache.write(userId, null)

    fun getCrossSigningKeys(
        userId: UserId,
    ): Flow<Set<StoredCrossSigningKeys>?> = crossSigningKeysCache.read(userId)

    suspend fun updateCrossSigningKeys(
        userId: UserId,
        updater: suspend (Set<StoredCrossSigningKeys>?) -> Set<StoredCrossSigningKeys>?
    ) = crossSigningKeysCache.write(userId, updater = updater)

    suspend fun deleteCrossSigningKeys(userId: UserId) = crossSigningKeysCache.write(userId, null)

    suspend fun getKeyVerificationState(
        key: Key,
    ): KeyVerificationState? {
        val keyId = key.keyId
        return keyId?.let {
            keyVerificationStateCache.read(
                KeyVerificationStateKey(
                    keyId = it,
                    keyAlgorithm = key.algorithm,
                )
            ).first()?.let { state ->
                if (state.keyValue == key.value) state
                else KeyVerificationState.Blocked(state.keyValue)
            }
        }
    }

    suspend fun saveKeyVerificationState(
        key: Key,
        state: KeyVerificationState
    ) {
        val keyId = key.keyId
        requireNotNull(keyId)
        keyVerificationStateCache.write(
            KeyVerificationStateKey(keyId = keyId, keyAlgorithm = key.algorithm), state
        )
    }

    suspend fun saveKeyChainLink(keyChainLink: KeyChainLink) =
        tm.writeOperationAsync(
            "saveKeyChainLink" + keyChainLink.run { signingUserId.full + signingKey.keyId + signingKey.value + signedUserId.full + signedKey.keyId + signedKey.value }
        ) {
            keyChainLinkRepository.save(keyChainLink)
        }

    suspend fun getKeyChainLinksBySigningKey(userId: UserId, signingKey: Key.Ed25519Key) =
        tm.readOperation { keyChainLinkRepository.getBySigningKey(userId, signingKey) }

    suspend fun deleteKeyChainLinksBySignedKey(userId: UserId, signedKey: Key.Ed25519Key) =
        tm.writeOperationAsync(
            "deleteKeyChainLinks" + userId.full + signedKey.keyId + signedKey.value
        ) { keyChainLinkRepository.deleteBySignedKey(userId, signedKey) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allSecretKeyRequests = secretKeyRequestCache.values
        .flatMapLatest {
            if (it.isEmpty()) flowOf(arrayOf())
            else combine(it.values) { transform -> transform }
        }
        .mapLatest { it.filterNotNull().toSet() }
        .stateIn(storeScope, SharingStarted.Eagerly, setOf())

    suspend fun addSecretKeyRequest(request: StoredSecretKeyRequest) {
        secretKeyRequestCache.write(request.content.requestId, request)
    }

    suspend fun deleteSecretKeyRequest(requestId: String) {
        secretKeyRequestCache.write(requestId, null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allRoomKeyRequests = roomKeyRequestCache.values
        .flatMapLatest {
            if (it.isEmpty()) flowOf(arrayOf())
            else combine(it.values) { transform -> transform }
        }
        .mapLatest { it.filterNotNull().toSet() }
        .stateIn(storeScope, SharingStarted.Eagerly, setOf())

    suspend fun addRoomKeyRequest(request: StoredRoomKeyRequest) {
        roomKeyRequestCache.write(request.content.requestId, request)
    }

    suspend fun deleteRoomKeyRequest(requestId: String) {
        roomKeyRequestCache.write(requestId, null)
    }
}