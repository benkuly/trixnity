package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryStateFlowCache
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
        MinimalRepositoryStateFlowCache(storeScope, deviceKeysRepository, tm, config.cacheExpireDurations.deviceKeys)
    private val crossSigningKeysCache = MinimalRepositoryStateFlowCache(
        storeScope,
        crossSigningKeysRepository,
        tm,
        config.cacheExpireDurations.crossSigningKeys
    )
    private val keyVerificationStateCache = MinimalRepositoryStateFlowCache(
        storeScope,
        keyVerificationStateRepository,
        tm,
        config.cacheExpireDurations.keyVerificationState
    )
    private val secretKeyRequestCache =
        MinimalRepositoryStateFlowCache(storeScope, secretKeyRequestRepository, tm, Duration.INFINITE)
    private val roomKeyRequestCache =
        MinimalRepositoryStateFlowCache(storeScope, roomKeyRequestRepository, tm, Duration.INFINITE)

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
        secretKeyRequestCache.init(tm.readOperation { secretKeyRequestRepository.getAll() }
            .associateBy { it.content.requestId })
        roomKeyRequestCache.init(tm.readOperation { roomKeyRequestRepository.getAll() }
            .associateBy { it.content.requestId })
    }

    override suspend fun clearCache() {
        tm.writeOperation {
            outdatedKeysRepository.deleteAll()
            deviceKeysRepository.deleteAll()
            crossSigningKeysRepository.deleteAll()
            keyChainLinkRepository.deleteAll()
            secretKeyRequestRepository.deleteAll()
            roomKeyRequestRepository.deleteAll()
            outdatedKeysRepository.deleteAll()
        }
        deviceKeysCache.reset()
        crossSigningKeysCache.reset()
        secretKeyRequestCache.reset()
        roomKeyRequestCache.reset()
    }

    override suspend fun deleteAll() {
        clearCache()
        tm.writeOperation {
            keyVerificationStateRepository.deleteAll()
        }
        keyVerificationStateCache.reset()
    }

    suspend fun updateOutdatedKeys(updater: suspend (Set<UserId>) -> Set<UserId>) {
        val newValue = _outdatedKeys.updateAndGet { updater(it) }
        tm.writeOperationAsync(outdatedKeysRepository.serializeKey(1)) {
            outdatedKeysRepository.save(1, newValue)
        }
    }

    fun getDeviceKeys(
        userId: UserId,
    ): Flow<Map<String, StoredDeviceKeys>?> = deviceKeysCache.get(userId)

    suspend fun updateDeviceKeys(
        userId: UserId,
        updater: suspend (Map<String, StoredDeviceKeys>?) -> Map<String, StoredDeviceKeys>?
    ) = deviceKeysCache.update(userId, updater = updater)

    fun getCrossSigningKeys(
        userId: UserId,
    ): Flow<Set<StoredCrossSigningKeys>?> = crossSigningKeysCache.get(userId)

    suspend fun updateCrossSigningKeys(
        userId: UserId,
        updater: suspend (Set<StoredCrossSigningKeys>?) -> Set<StoredCrossSigningKeys>?
    ) = crossSigningKeysCache.update(userId, updater = updater)

    suspend fun getKeyVerificationState(
        key: Key,
    ): KeyVerificationState? {
        val keyId = key.keyId
        return keyId?.let {
            keyVerificationStateCache.get(
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
        keyVerificationStateCache.save(
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
    val allSecretKeyRequests = secretKeyRequestCache.cache
        .flatMapLatest {
            if (it.isEmpty()) flowOf(arrayOf())
            else combine(it.values) { transform -> transform }
        }
        .mapLatest { it.filterNotNull().toSet() }
        .stateIn(storeScope, SharingStarted.Eagerly, setOf())

    suspend fun addSecretKeyRequest(request: StoredSecretKeyRequest) {
        secretKeyRequestCache.save(request.content.requestId, request)
    }

    suspend fun deleteSecretKeyRequest(requestId: String) {
        secretKeyRequestCache.save(requestId, null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allRoomKeyRequests = roomKeyRequestCache.cache
        .flatMapLatest {
            if (it.isEmpty()) flowOf(arrayOf())
            else combine(it.values) { transform -> transform }
        }
        .mapLatest { it.filterNotNull().toSet() }
        .stateIn(storeScope, SharingStarted.Eagerly, setOf())

    suspend fun addRoomKeyRequest(request: StoredRoomKeyRequest) {
        roomKeyRequestCache.save(request.content.requestId, request)
    }

    suspend fun deleteRoomKeyRequest(requestId: String) {
        roomKeyRequestCache.save(requestId, null)
    }
}