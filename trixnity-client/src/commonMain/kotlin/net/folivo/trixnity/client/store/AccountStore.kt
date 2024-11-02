package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Duration

class AccountStore(
    repository: AccountRepository,
    tm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : Store {
    private val accountCache = MinimalRepositoryObservableCache(repository, tm, storeScope, Duration.INFINITE)

    suspend fun getAccount() = accountCache.read(1).first()
    fun getAccountAsFlow() = accountCache.read(1)
    suspend fun updateAccount(updater: suspend (Account?) -> Account?) = accountCache.write(1) { account ->
        updater(account)
    }

    override suspend fun clearCache() {}

    override suspend fun deleteAll() {
        accountCache.deleteAll()
    }
}