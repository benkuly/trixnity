package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.DeleteByRoomIdFullRepository
import net.folivo.trixnity.client.store.repository.DeleteByRoomIdMapRepository
import net.folivo.trixnity.client.store.repository.DeleteByRoomIdMinimalRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private class DeleteByRoomIdRepositoryObservableCacheIndex<K>(
    private val keyMapper: (K) -> RoomId,
) : ObservableCacheIndex<K> {

    private val values = ConcurrentObservableMap<RoomId, ConcurrentObservableSet<K>>()

    override suspend fun onPut(key: K) {
        val roomId = keyMapper(key)
        values.getOrPut(roomId) { ConcurrentObservableSet() }
            .add(key)
    }

    override suspend fun onRemove(key: K, stale: Boolean) {
        val roomId = keyMapper(key)
        values.update(roomId) { mapping ->
            mapping?.remove(key)
            if (mapping == null || mapping.size() == 0) null
            else mapping
        }
    }

    override suspend fun onRemoveAll() {
        values.removeAll()
    }

    private val zeroStateFlow = MutableStateFlow(0)
    override suspend fun getSubscriptionCount(key: K): StateFlow<Int> = zeroStateFlow

    suspend fun getMapping(roomId: RoomId): Set<K> =
        values.getOrPut(roomId) { ConcurrentObservableSet() }
            .values.first()

    override suspend fun collectStatistic(): ObservableCacheIndexStatistic? = null
}

internal class MinimalDeleteByRoomIdRepositoryObservableCache<K, V>(
    private val repository: DeleteByRoomIdMinimalRepository<K, V>,
    private val tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    roomIdMapper: (K) -> RoomId,
) : MinimalRepositoryObservableCache<K, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
) {

    private val roomIdIndex: DeleteByRoomIdRepositoryObservableCacheIndex<K> =
        DeleteByRoomIdRepositoryObservableCacheIndex(roomIdMapper)

    init {
        addIndex(roomIdIndex)
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        coroutineScope {
            launch {
                tm.writeTransaction { repository.deleteByRoomId(roomId) }
            }
            launch {
                roomIdIndex.getMapping(roomId).forEach {
                    updateAndGet(
                        key = it,
                        updater = { null },
                    )
                }
            }
        }
    }
}

internal class FullDeleteByRoomIdRepositoryObservableCache<K, V>(
    private val repository: DeleteByRoomIdFullRepository<K, V>,
    private val tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    valueToKeyMapper: (V) -> K,
    roomIdMapper: (K) -> RoomId,
) : FullRepositoryObservableCache<K, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
    valueToKeyMapper = valueToKeyMapper
) {

    private val roomIdIndex: DeleteByRoomIdRepositoryObservableCacheIndex<K> =
        DeleteByRoomIdRepositoryObservableCacheIndex(roomIdMapper)

    init {
        addIndex(roomIdIndex)
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        coroutineScope {
            launch {
                tm.writeTransaction { repository.deleteByRoomId(roomId) }
            }
            launch {
                roomIdIndex.getMapping(roomId).forEach {
                    updateAndGet(
                        key = it,
                        updater = { null },
                    )
                }
            }
        }
    }
}

internal class MapDeleteByRoomIdRepositoryObservableCache<K1, K2, V>(
    private val repository: DeleteByRoomIdMapRepository<K1, K2, V>,
    private val tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    roomIdMapper: (MapRepositoryCoroutinesCacheKey<K1, K2>) -> RoomId,
) : MapRepositoryObservableCache<K1, K2, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
) {
    private val roomIdIndex: DeleteByRoomIdRepositoryObservableCacheIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> =
        DeleteByRoomIdRepositoryObservableCacheIndex(roomIdMapper)

    init {
        addIndex(roomIdIndex)
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        coroutineScope {
            launch {
                tm.writeTransaction { repository.deleteByRoomId(roomId) }
            }
            launch {
                roomIdIndex.getMapping(roomId).forEach {
                    updateAndGet(
                        key = it,
                        updater = { null },
                    )
                }
            }
        }
    }
}