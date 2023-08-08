package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.MapDeleteByRoomIdRepository
import net.folivo.trixnity.client.store.repository.MinimalDeleteByRoomIdRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private class DeleteByRoomIdRepositoryObservableMapIndex<K>(
    cacheScope: CoroutineScope,
    private val keyMapper: (K) -> RoomId,
) : ObservableMapIndex<K> {

    private val roomIdMapping = ObservableMap<RoomId, Set<K>>(cacheScope)

    override suspend fun onPut(key: K) {
        val roomId = keyMapper(key)
        roomIdMapping.update(roomId) { mapping ->
            mapping.orEmpty() + key
        }
    }

    override suspend fun onRemove(key: K) {
        val roomId = keyMapper(key)
        roomIdMapping.update(roomId) { mapping ->
            val newMapping = mapping.orEmpty() - key
            newMapping.ifEmpty { null }
        }
    }

    override suspend fun onRemoveAll() {
        roomIdMapping.removeAll()
    }

    private val zeroStateFlow = MutableStateFlow(0)
    override suspend fun getSubscriptionCount(key: K): StateFlow<Int> = zeroStateFlow

    suspend fun getMapping(roomId: RoomId): Set<K> =
        roomIdMapping.get(roomId).orEmpty()
}

internal class MinimalDeleteByRoomIdRepositoryObservableCache<K, V>(
    private val repository: MinimalDeleteByRoomIdRepository<K, V>,
    private val tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    keyMapper: (K) -> RoomId,
) : MinimalRepositoryObservableCache<K, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
) {

    private val roomIdIndex: DeleteByRoomIdRepositoryObservableMapIndex<K> =
        DeleteByRoomIdRepositoryObservableMapIndex(cacheScope, keyMapper)

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
                        get = { null },
                        persist = { },
                    )
                }
            }
        }
    }
}

internal class MapDeleteByRoomIdRepositoryObservableCache<K1, K2, V>(
    private val repository: MapDeleteByRoomIdRepository<K1, K2, V>,
    private val tm: RepositoryTransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    keyMapper: (MapRepositoryCoroutinesCacheKey<K1, K2>) -> RoomId,
) : MapRepositoryObservableCache<K1, K2, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
) {
    private val roomIdIndex: DeleteByRoomIdRepositoryObservableMapIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> =
        DeleteByRoomIdRepositoryObservableMapIndex(cacheScope, keyMapper)

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
                        get = { null },
                        persist = { },
                    )
                }
            }
        }
    }
}