package net.folivo.trixnity.client.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.SecretType
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

private val log = KotlinLogging.logger { }

class KeyStore(
    outdatedKeysRepository: OutdatedKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    crossSigningKeysRepository: CrossSigningKeysRepository,
    keyVerificationStateRepository: KeyVerificationStateRepository,
    private val keyChainLinkRepository: KeyChainLinkRepository,
    secretsRepository: SecretsRepository,
    secretKeyRequestRepository: SecretKeyRequestRepository,
    roomKeyRequestRepository: RoomKeyRequestRepository,
    private val tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope
) : Store {
    private val outdatedKeysCache =
        MinimalRepositoryObservableCache(
            repository = outdatedKeysRepository,
            tm = tm,
            cacheScope = storeScope,
            expireDuration = Duration.INFINITE
        )
    private val secretsCache =
        MinimalRepositoryObservableCache(
            repository = secretsRepository,
            tm = tm,
            cacheScope = storeScope,
            expireDuration = Duration.INFINITE
        )
    private val deviceKeysCache =
        MinimalRepositoryObservableCache(
            repository = deviceKeysRepository,
            tm = tm,
            cacheScope = storeScope,
            expireDuration = config.cacheExpireDurations.deviceKeys
        )
    private val crossSigningKeysCache = MinimalRepositoryObservableCache(
        repository = crossSigningKeysRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = config.cacheExpireDurations.crossSigningKeys
    )
    private val keyVerificationStateCache = MinimalRepositoryObservableCache(
        repository = keyVerificationStateRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = config.cacheExpireDurations.keyVerificationState
    )
    private val secretKeyRequestCache = FullRepositoryObservableCache(
        repository = secretKeyRequestRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = config.cacheExpireDurations.secretKeyRequest
    ) { it.content.requestId }
    private val roomKeyRequestCache = FullRepositoryObservableCache(
        repository = roomKeyRequestRepository,
        tm = tm,
        cacheScope = storeScope,
        expireDuration = config.cacheExpireDurations.roomKeyRequest
    ) { it.content.requestId }

    override suspend fun clearCache() {
        tm.writeTransaction {
            keyChainLinkRepository.deleteAll()
        }
        outdatedKeysCache.deleteAll()
        deviceKeysCache.deleteAll()
        crossSigningKeysCache.deleteAll()
        secretKeyRequestCache.deleteAll()
        roomKeyRequestCache.deleteAll()
    }

    override suspend fun deleteAll() {
        clearCache()
        secretsCache.deleteAll()
        keyVerificationStateCache.deleteAll()
    }

    suspend fun getOutdatedKeys(): Set<UserId> = outdatedKeysCache.read(1).first().orEmpty()
    fun getOutdatedKeysFlow(): Flow<Set<UserId>> = outdatedKeysCache.read(1).map { it.orEmpty() }
    suspend fun updateOutdatedKeys(updater: suspend (Set<UserId>) -> Set<UserId>) =
        outdatedKeysCache.write(1) {
            updater(it.orEmpty())
        }

    suspend fun getSecrets(): Map<SecretType, StoredSecret> = secretsCache.read(1).first().orEmpty()
    fun getSecretsFlow(): Flow<Map<SecretType, StoredSecret>> = secretsCache.read(1).map { it.orEmpty() }
    suspend fun updateSecrets(updater: suspend (Map<SecretType, StoredSecret>) -> Map<SecretType, StoredSecret>) =
        secretsCache.write(1) {
            updater(it ?: mapOf())
        }

    object SkipOutdatedKeys : CoroutineContext.Element, CoroutineContext.Key<SkipOutdatedKeys> {
        override val key: CoroutineContext.Key<*> = this
    }

    private suspend fun waitForUpdateOutdatedKey(user: UserId) {
        log.debug { "wait for outdated keys of $user" }
        getOutdatedKeysFlow().first { !it.contains(user) }
        log.trace { "finished wait for outdated keys of $user" }
    }

    fun getDeviceKeys(
        userId: UserId,
    ): Flow<Map<String, StoredDeviceKeys>?> =
        flow {
            if (currentCoroutineContext()[SkipOutdatedKeys] == null) {
                val keys = deviceKeysCache.read(userId).first()
                if (keys == null) {
                    updateOutdatedKeys { it + userId }
                }
                waitForUpdateOutdatedKey(userId)
            }
            emitAll(deviceKeysCache.read(userId))
        }

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
    ): Flow<Set<StoredCrossSigningKeys>?> =
        flow {
            if (currentCoroutineContext()[SkipOutdatedKeys] == null) {
                val keys = crossSigningKeysCache.read(userId).first()
                if (keys == null) {
                    updateOutdatedKeys { it + userId }
                }
                waitForUpdateOutdatedKey(userId)
            }
            emitAll(crossSigningKeysCache.read(userId))
        }

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
        tm.writeTransaction {
            keyChainLinkRepository.save(keyChainLink)
        }

    suspend fun getKeyChainLinksBySigningKey(userId: UserId, signingKey: Key.Ed25519Key) =
        tm.readTransaction { keyChainLinkRepository.getBySigningKey(userId, signingKey) }

    suspend fun deleteKeyChainLinksBySignedKey(userId: UserId, signedKey: Key.Ed25519Key) =
        tm.writeTransaction { keyChainLinkRepository.deleteBySignedKey(userId, signedKey) }

    fun getAllSecretKeyRequestsFlow() = secretKeyRequestCache.readAll().flattenValues()
    suspend fun getAllSecretKeyRequests() = getAllSecretKeyRequestsFlow().first()

    suspend fun addSecretKeyRequest(request: StoredSecretKeyRequest) {
        secretKeyRequestCache.write(request.content.requestId, request)
    }

    suspend fun deleteSecretKeyRequest(requestId: String) {
        secretKeyRequestCache.write(requestId, null)
    }

    fun getAllRoomKeyRequestsFlow() = roomKeyRequestCache.readAll().flattenValues()
    suspend fun getAllRoomKeyRequests() = getAllRoomKeyRequestsFlow().first()

    suspend fun addRoomKeyRequest(request: StoredRoomKeyRequest) {
        roomKeyRequestCache.write(request.content.requestId, request)
    }

    suspend fun deleteRoomKeyRequest(requestId: String) {
        roomKeyRequestCache.write(requestId, null)
    }
}