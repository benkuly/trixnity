package de.connect2x.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.store.cache.MinimalRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Clock
import kotlin.time.Duration

class AuthenticationStore(
    repository: AuthenticationRepository,
    tm: RepositoryTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val authenticationCache =
        MinimalRepositoryObservableCache(repository, tm, storeScope, clock, Duration.INFINITE)
            .also(statisticCollector::addCache)

    fun getAuthenticationAsFlow() = authenticationCache.get(1)

    suspend fun getAuthentication() = authenticationCache.get(1).first()
    suspend fun updateAuthentication(updater: suspend (Authentication?) -> Authentication?) =
        authenticationCache.update(1) { authentication ->
            updater(authentication)
        }

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        authenticationCache.deleteAll()
    }
}