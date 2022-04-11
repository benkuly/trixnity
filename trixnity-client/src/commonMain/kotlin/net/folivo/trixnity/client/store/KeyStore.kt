package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

class KeyStore(
    private val outdatedKeysRepository: OutdatedKeysRepository,
    private val deviceKeysRepository: DeviceKeysRepository,
    private val crossSigningKeysRepository: CrossSigningKeysRepository,
    private val keyVerificationStateRepository: KeyVerificationStateRepository,
    private val keyChainLinkRepository: KeyChainLinkRepository,
    private val secretsRepository: SecretsRepository,
    private val secretKeyRequestRepository: SecretKeyRequestRepository,
    private val rtm: RepositoryTransactionManager,
    private val storeScope: CoroutineScope
) {
    val outdatedKeys = MutableStateFlow<Set<UserId>>(setOf())
    val secrets = MutableStateFlow<Map<AllowedSecretType, StoredSecret>>(mapOf())
    private val deviceKeysCache = RepositoryStateFlowCache(storeScope, deviceKeysRepository, rtm)
    private val crossSigningKeysCache = RepositoryStateFlowCache(storeScope, crossSigningKeysRepository, rtm)
    private val keyVerificationStateCache = RepositoryStateFlowCache(storeScope, keyVerificationStateRepository, rtm)
    private val secretKeyRequestCache = RepositoryStateFlowCache(storeScope, secretKeyRequestRepository, rtm, true)

    suspend fun init() {
        outdatedKeys.value = rtm.transaction { outdatedKeysRepository.get(1) ?: setOf() }
        secrets.value = rtm.transaction { secretsRepository.get(1) ?: mapOf() }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            outdatedKeys.collect { rtm.transaction { outdatedKeysRepository.save(1, it) } }
        }
        storeScope.launch(start = UNDISPATCHED) {
            secrets.collect { rtm.transaction { secretsRepository.save(1, it) } }
        }
        secretKeyRequestCache.init(rtm.transaction { secretKeyRequestRepository.getAll() }
            .associateBy { it.content.requestId })
    }

    suspend fun deleteAll() {
        rtm.transaction {
            outdatedKeysRepository.deleteAll()
            deviceKeysRepository.deleteAll()
            crossSigningKeysRepository.deleteAll()
            keyVerificationStateRepository.deleteAll()
            keyChainLinkRepository.deleteAll()
            secretsRepository.deleteAll()
            secretKeyRequestRepository.deleteAll()
        }
        outdatedKeys.value = setOf()
        secrets.value = mapOf()
        deviceKeysCache.reset()
        crossSigningKeysCache.reset()
        keyVerificationStateCache.reset()
        secretKeyRequestCache.reset()
    }

    suspend fun deleteNonLocal() {
        rtm.transaction {
            outdatedKeysRepository.deleteAll()
            deviceKeysRepository.deleteAll()
            crossSigningKeysRepository.deleteAll()
            keyChainLinkRepository.deleteAll()
            secretKeyRequestRepository.deleteAll()
        }
        outdatedKeys.value = setOf()
        deviceKeysCache.reset()
        crossSigningKeysCache.reset()
        secretKeyRequestCache.reset()
    }

    suspend fun getDeviceKeys(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<Map<String, StoredDeviceKeys>?> = deviceKeysCache.get(userId, scope = scope)

    suspend fun getDeviceKeys(
        userId: UserId,
    ): Map<String, StoredDeviceKeys>? = deviceKeysCache.get(userId)

    suspend fun updateDeviceKeys(
        userId: UserId,
        updater: suspend (Map<String, StoredDeviceKeys>?) -> Map<String, StoredDeviceKeys>?
    ) = deviceKeysCache.update(userId, updater = updater)

    suspend fun getCrossSigningKeys(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<Set<StoredCrossSigningKeys>?> = crossSigningKeysCache.get(userId, scope = scope)

    suspend fun getCrossSigningKeys(
        userId: UserId,
    ): Set<StoredCrossSigningKeys>? = crossSigningKeysCache.get(userId)

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
                VerifiedKeysRepositoryKey(
                    keyId = it,
                    keyAlgorithm = key.algorithm,
                )
            )?.let { state ->
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
            VerifiedKeysRepositoryKey(keyId = keyId, keyAlgorithm = key.algorithm)
        ) { state }
    }

    suspend fun saveKeyChainLink(keyChainLink: KeyChainLink) =
        rtm.transaction { keyChainLinkRepository.save(keyChainLink) }

    suspend fun getKeyChainLinksBySigningKey(userId: UserId, signingKey: Key.Ed25519Key) =
        rtm.transaction { keyChainLinkRepository.getBySigningKey(userId, signingKey) }

    suspend fun deleteKeyChainLinksBySignedKey(userId: UserId, signedKey: Key.Ed25519Key) =
        rtm.transaction { keyChainLinkRepository.deleteBySignedKey(userId, signedKey) }

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
}