package net.folivo.trixnity.client.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.ServerVersionsRepository
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

class ServerVersionsStore(
    private val api: MatrixClientServerApiClient,
    repository: ServerVersionsRepository,
    tm: RepositoryTransactionManager,
    private val storeScope: CoroutineScope
) : Store {
    private val serverVersionsCache = MinimalRepositoryObservableCache(repository, tm, storeScope, Duration.INFINITE)

    override suspend fun init() {
        storeScope.launch {
            while (true) {
                val newVersions = api.server.getVersions().getOrNull()
                if (newVersions != null) {
                    serverVersionsCache.write(1, ServerVersions(newVersions.versions, newVersions.unstableFeatures))
                    delay(1.days)
                } else {
                    log.warn { "failed to get server versions" }
                    delay(10.minutes)
                }
            }
        }
    }

    fun getServerVersionsFlow() = serverVersionsCache.read(1).filterNotNull()
    suspend fun getServerVersions() = getServerVersionsFlow().first()

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        serverVersionsCache.deleteAll()
    }
}