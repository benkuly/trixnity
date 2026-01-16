package de.connect2x.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
import de.connect2x.trixnity.core.model.UserId
import kotlin.time.Clock

class UserPresenceStore(
    repository: UserPresenceRepository,
    tm: RepositoryTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val presenceCache =
        MinimalRepositoryObservableCache(
            repository = repository,
            tm = tm,
            cacheScope = storeScope,
            clock = clock,
            expireDuration = config.cacheExpireDurations.presence
        ).also(statisticCollector::addCache)

    fun getPresence(userId: UserId) = presenceCache.get(userId)
    suspend fun setPresence(userId: UserId, userPresence: UserPresence) =
        presenceCache.set(userId, userPresence)

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        presenceCache.deleteAll()
    }
}