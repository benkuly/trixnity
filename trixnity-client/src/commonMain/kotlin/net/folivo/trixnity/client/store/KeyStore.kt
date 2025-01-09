package net.folivo.trixnity.client.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.store.cache.FullRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
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
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val outdatedKeysCache =
        MinimalRepositoryObservableCache(
            repository = outdatedKeysRepository,
            tm = tm,
            cacheScope = storeScope,
            clock = clock,
            expireDuration = Duration.INFINITE
        ).also(statisticCollector::addCache)
    private val secretsCache =
        MinimalRepositoryObservableCache(
            repository = secretsRepository,
            tm = tm,
            cacheScope = storeScope,
            clock = clock,
            expireDuration = Duration.INFINITE
        ).also(statisticCollector::addCache)
    private val deviceKeysCache =
        MinimalRepositoryObservableCache(
            repository = deviceKeysRepository,
            tm = tm,
            cacheScope = storeScope,
            clock = clock,
            expireDuration = config.cacheExpireDurations.deviceKeys
        ).also(statisticCollector::addCache)
    private val crossSigningKeysCache = MinimalRepositoryObservableCache(
        repository = crossSigningKeysRepository,
        tm = tm,
        cacheScope = storeScope,
        clock = clock,
        expireDuration = config.cacheExpireDurations.crossSigningKeys
    ).also(statisticCollector::addCache)
    private val keyVerificationStateCache = MinimalRepositoryObservableCache(
        repository = keyVerificationStateRepository,
        tm = tm,
        cacheScope = storeScope,
        clock = clock,
        expireDuration = config.cacheExpireDurations.keyVerificationState
    ).also(statisticCollector::addCache)
    private val secretKeyRequestCache = FullRepositoryObservableCache(
        repository = secretKeyRequestRepository,
        tm = tm,
        cacheScope = storeScope,
        clock = clock,
        expireDuration = config.cacheExpireDurations.secretKeyRequest
    ) { it.content.requestId }.also(statisticCollector::addCache)
    private val roomKeyRequestCache = FullRepositoryObservableCache(
        repository = roomKeyRequestRepository,
        tm = tm,
        cacheScope = storeScope,
        clock = clock,
        expireDuration = config.cacheExpireDurations.roomKeyRequest
    ) { it.content.requestId }.also(statisticCollector::addCache)

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

    suspend fun getOutdatedKeys(): Set<UserId> = outdatedKeysCache.get(1).first().orEmpty()
    fun getOutdatedKeysFlow(): Flow<Set<UserId>> = outdatedKeysCache.get(1).map { it.orEmpty() }
    suspend fun updateOutdatedKeys(updater: suspend (Set<UserId>) -> Set<UserId>) =
        outdatedKeysCache.update(1) {
            updater(it.orEmpty())
        }

    suspend fun getSecrets(): Map<SecretType, StoredSecret> = secretsCache.get(1).first().orEmpty()
    fun getSecretsFlow(): Flow<Map<SecretType, StoredSecret>> = secretsCache.get(1).map { it.orEmpty() }
    suspend fun updateSecrets(updater: suspend (Map<SecretType, StoredSecret>) -> Map<SecretType, StoredSecret>) =
        secretsCache.update(1) {
            updater(it ?: mapOf())
        }

    /**
     * This prevents deadlocks when no parallel write transactions are allowed, but a second transaction is needed to update outdated keys.
     */
    object SkipOutdatedKeys : CoroutineContext.Element, CoroutineContext.Key<SkipOutdatedKeys> {
        override val key: CoroutineContext.Key<*> = this
    }

    private suspend fun waitForUpdateOutdatedKey(userId: UserId, reason: String, keysAreNull: suspend () -> Boolean) {
        if (currentCoroutineContext()[SkipOutdatedKeys] == null) {
            if (keysAreNull()) {
                log.trace { "add $userId to outdated keys, because key ($reason) not found" }
                updateOutdatedKeys { it + userId }
            }
            log.debug { "wait for outdated keys ($reason) of $userId" }
            getOutdatedKeysFlow().first { !it.contains(userId) }
            log.trace { "finished wait for outdated keys ($reason) of $userId" }
        }
    }

    fun getDeviceKeys(
        userId: UserId,
    ): Flow<Map<String, StoredDeviceKeys>?> =
        flow {
            waitForUpdateOutdatedKey(userId, "device keys") {
                deviceKeysCache.get(userId).first() == null
            }
            emitAll(deviceKeysCache.get(userId))
        }

    suspend fun updateDeviceKeys(
        userId: UserId,
        updater: suspend (Map<String, StoredDeviceKeys>?) -> Map<String, StoredDeviceKeys>?
    ) = deviceKeysCache.update(userId, updater = updater)

    suspend fun saveDeviceKeys(
        userId: UserId,
        deviceKeys: Map<String, StoredDeviceKeys>
    ) = deviceKeysCache.set(userId, deviceKeys)

    suspend fun deleteDeviceKeys(userId: UserId) = deviceKeysCache.set(userId, null)

    fun getCrossSigningKeys(
        userId: UserId,
    ): Flow<Set<StoredCrossSigningKeys>?> =
        flow {
            waitForUpdateOutdatedKey(userId, "cross singing keys") {
                crossSigningKeysCache.get(userId).first() == null
            }
            emitAll(crossSigningKeysCache.get(userId))
        }

    suspend fun updateCrossSigningKeys(
        userId: UserId,
        updater: suspend (Set<StoredCrossSigningKeys>?) -> Set<StoredCrossSigningKeys>?
    ) = crossSigningKeysCache.update(userId, updater = updater)

    suspend fun deleteCrossSigningKeys(userId: UserId) = crossSigningKeysCache.set(userId, null)

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
        keyVerificationStateCache.set(
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
        secretKeyRequestCache.set(request.content.requestId, request)
    }

    suspend fun deleteSecretKeyRequest(requestId: String) {
        secretKeyRequestCache.set(requestId, null)
    }

    fun getAllRoomKeyRequestsFlow() = roomKeyRequestCache.readAll().flattenValues()
    suspend fun getAllRoomKeyRequests() = getAllRoomKeyRequestsFlow().first()

    suspend fun addRoomKeyRequest(request: StoredRoomKeyRequest) {
        roomKeyRequestCache.set(request.content.requestId, request)
    }

    suspend fun deleteRoomKeyRequest(requestId: String) {
        roomKeyRequestCache.set(requestId, null)
    }
}