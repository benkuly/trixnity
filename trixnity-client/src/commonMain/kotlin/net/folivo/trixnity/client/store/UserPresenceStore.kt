package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.UserPresenceRepository
import net.folivo.trixnity.core.model.UserId
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