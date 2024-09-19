package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.ServerVersionsRepository
import kotlin.time.Duration

class ServerVersionsStore(
    repository: ServerVersionsRepository,
    tm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val serverVersionsCache = MinimalRepositoryObservableCache(repository, tm, storeScope, Duration.INFINITE)

    suspend fun setServerVersion(serverVersions: ServerVersions) =
        serverVersionsCache.write(1, ServerVersions(serverVersions.versions, serverVersions.unstableFeatures))

    fun getServerVersionsFlow() = serverVersionsCache.read(1).filterNotNull()
    suspend fun getServerVersions() = getServerVersionsFlow().first()

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        serverVersionsCache.deleteAll()
    }
}