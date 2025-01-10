package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration

class AccountStore(
    repository: AccountRepository,
    tm: RepositoryTransactionManager,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val accountCache = MinimalRepositoryObservableCache(repository, tm, storeScope, clock, Duration.INFINITE)
        .also(statisticCollector::addCache)

    suspend fun getAccount() = accountCache.get(1).first()
    fun getAccountAsFlow() = accountCache.get(1)
    suspend fun updateAccount(updater: suspend (Account?) -> Account?) = accountCache.update(1) { account ->
        updater(account)
    }

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        accountCache.deleteAll()
    }
}