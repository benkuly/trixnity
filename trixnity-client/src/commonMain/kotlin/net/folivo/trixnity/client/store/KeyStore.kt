package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.SecretType

class KeyStore(
    private val outdatedKeysRepository: OutdatedKeysRepository,
    private val deviceKeysRepository: DeviceKeysRepository,
    private val crossSigningKeysRepository: CrossSigningKeysRepository,
    private val keyVerificationStateRepository: KeyVerificationStateRepository,
    private val keyChainLinkRepository: KeyChainLinkRepository,
    private val secretsRepository: SecretsRepository,
    private val secretKeyRequestRepository: SecretKeyRequestRepository,
    private val roomKeyRequestRepository: RoomKeyRequestRepository,
    private val rtm: RepositoryTransactionManager,
    private val storeScope: CoroutineScope
) : Store {
    val outdatedKeys = MutableStateFlow<Set<UserId>>(setOf())
    val secrets = MutableStateFlow<Map<SecretType, StoredSecret>>(mapOf())
    private val deviceKeysCache = RepositoryStateFlowCache(storeScope, deviceKeysRepository, rtm)
    private val crossSigningKeysCache = RepositoryStateFlowCache(storeScope, crossSigningKeysRepository, rtm)
    private val keyVerificationStateCache = RepositoryStateFlowCache(storeScope, keyVerificationStateRepository, rtm)
    private val secretKeyRequestCache = RepositoryStateFlowCache(storeScope, secretKeyRequestRepository, rtm, true)
    private val roomKeyRequestCache = RepositoryStateFlowCache(storeScope, roomKeyRequestRepository, rtm, true)

    override suspend fun init() {
        outdatedKeys.value = rtm.readTransaction { outdatedKeysRepository.get(1) ?: setOf() }
        secrets.value = rtm.readTransaction { secretsRepository.get(1) ?: mapOf() }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            outdatedKeys.collect { rtm.writeTransaction { outdatedKeysRepository.save(1, it) } }
        }
        storeScope.launch(start = UNDISPATCHED) {
            secrets.collect { rtm.writeTransaction { secretsRepository.save(1, it) } }
        }
        secretKeyRequestCache.init(rtm.readTransaction { secretKeyRequestRepository.getAll() }
            .associateBy { it.content.requestId })
        roomKeyRequestCache.init(rtm.readTransaction { roomKeyRequestRepository.getAll() }
            .associateBy { it.content.requestId })
    }

    override suspend fun clearCache() {
        rtm.writeTransaction {
            outdatedKeysRepository.deleteAll()
            deviceKeysRepository.deleteAll()
            crossSigningKeysRepository.deleteAll()
            keyChainLinkRepository.deleteAll()
            secretKeyRequestRepository.deleteAll()
            roomKeyRequestRepository.deleteAll()
        }
        outdatedKeys.value = setOf()
        deviceKeysCache.reset()
        crossSigningKeysCache.reset()
        secretKeyRequestCache.reset()
        roomKeyRequestCache.reset()
    }

    override suspend fun deleteAll() {
        clearCache()
        rtm.writeTransaction {
            keyVerificationStateRepository.deleteAll()
        }
        keyVerificationStateCache.reset()
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
        keyVerificationStateCache.update(
            KeyVerificationStateKey(keyId = keyId, keyAlgorithm = key.algorithm)
        ) { state }
    }

    suspend fun saveKeyChainLink(keyChainLink: KeyChainLink) =
        rtm.writeTransaction { keyChainLinkRepository.save(keyChainLink) }

    suspend fun getKeyChainLinksBySigningKey(userId: UserId, signingKey: Key.Ed25519Key) =
        rtm.readTransaction { keyChainLinkRepository.getBySigningKey(userId, signingKey) }

    suspend fun deleteKeyChainLinksBySignedKey(userId: UserId, signedKey: Key.Ed25519Key) =
        rtm.writeTransaction { keyChainLinkRepository.deleteBySignedKey(userId, signedKey) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allSecretKeyRequests = secretKeyRequestCache.cache
        .flatMapLatest {
            if (it.isEmpty()) flowOf(arrayOf())
            else combine(it.values) { transform -> transform }
        }
        .mapLatest { it.filterNotNull().toSet() }
        .stateIn(storeScope, SharingStarted.Eagerly, setOf())

    suspend fun addSecretKeyRequest(request: StoredSecretKeyRequest) {
        secretKeyRequestCache.update(request.content.requestId) { request }
    }

    suspend fun deleteSecretKeyRequest(requestId: String) {
        secretKeyRequestCache.update(requestId) { null }
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
        roomKeyRequestCache.update(request.content.requestId) { request }
    }

    suspend fun deleteRoomKeyRequest(requestId: String) {
        roomKeyRequestCache.update(requestId) { null }
    }
}