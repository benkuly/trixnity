package de.connect2x.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RootStore(private val stores: List<Store>) : Store {
    private val hasBeenInit = MutableStateFlow(false)
    override suspend fun init(coroutineScope: CoroutineScope) {
        if (hasBeenInit.getAndUpdate { true }.not())
            stores.forEach { it.init(coroutineScope) }
    }

    private val clearCacheMutex = Mutex()
    override suspend fun clearCache() {
        clearCacheMutex.withLock {
            stores.forEach { it.clearCache() }
        }
    }

    private val deleteAllMutex = Mutex()
    override suspend fun deleteAll() {
        deleteAllMutex.withLock {
            stores.forEach { it.deleteAll() }
        }
    }
}