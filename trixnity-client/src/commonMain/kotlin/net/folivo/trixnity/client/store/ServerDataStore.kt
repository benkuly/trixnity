package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.ServerDataRepository
import kotlin.time.Duration

class ServerDataStore(
    repository: ServerDataRepository,
    tm: RepositoryTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope
) : Store {
    private val serverDataCache = MinimalRepositoryObservableCache(repository, tm, storeScope, Duration.INFINITE)
        .also(statisticCollector::addCache)

    suspend fun setServerData(serverData: ServerData) = serverDataCache.write(1, serverData)

    fun getServerDataFlow() = serverDataCache.read(1).filterNotNull()
    suspend fun getServerData() = getServerDataFlow().first()

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        serverDataCache.deleteAll()
    }
}