package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RootStore(private val stores: List<IStore>) : IStore {
    private val hasBeenInit = MutableStateFlow(false)
    override suspend fun init() {
        if (hasBeenInit.getAndUpdate { true }.not())
            stores.forEach { it.init() }
    }

    private val clearCacheMutex = Mutex()
    override suspend fun clearCache() {
        clearCacheMutex.withLock {
            stores.forEach { it.init() }
        }
    }

    private val deleteAllMutex = Mutex()
    override suspend fun deleteAll() {
        deleteAllMutex.withLock {
            stores.forEach { it.deleteAll() }
        }
    }
}