package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.MapDeleteByRoomIdRepository
import net.folivo.trixnity.client.store.repository.MinimalDeleteByRoomIdRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private class DeleteByRoomIdRepositoryCoroutineCacheValuesIndex<K>(
    private val keyMapper: (K) -> RoomId,
) : CoroutineCacheValuesIndex<K> {

    private val roomIdMapping = MutableStateFlow<Map<RoomId, Set<K>>>(emptyMap())

    override suspend fun onPut(key: K): Unit =
        roomIdMapping.update { mappings ->
            val roomId = keyMapper(key)
            mappings + (roomId to (mappings[roomId].orEmpty() + key))
        }

    override suspend fun onRemove(key: K) {
        roomIdMapping.update { mappings ->
            val roomId = keyMapper(key)
            val newMapping = mappings[roomId].orEmpty() - key
            if (newMapping.isEmpty()) mappings - roomId
            else mappings + (roomId to newMapping)
        }
    }

    private val zeroStateFlow = MutableStateFlow(0)
    override suspend fun getSubscriptionCount(key: K): StateFlow<Int> = zeroStateFlow

    fun getMapping(roomId: RoomId): Set<K> =
        roomIdMapping.value[roomId].orEmpty()
}

class MinimalDeleteByRoomIdRepositoryCoroutineCache<K, V>(
    private val repository: MinimalDeleteByRoomIdRepository<K, V>,
    private val tm: TransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    keyMapper: (K) -> RoomId,
) : MinimalRepositoryCoroutineCache<K, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
) {

    private val roomIdIndex: DeleteByRoomIdRepositoryCoroutineCacheValuesIndex<K> =
        DeleteByRoomIdRepositoryCoroutineCacheValuesIndex(keyMapper)

    init {
        addIndex(roomIdIndex)
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        coroutineScope {
            launch {
                tm.writeOperation { repository.deleteByRoomId(roomId) }
            }
            launch {
                roomIdIndex.getMapping(roomId).forEach {
                    updateAndGet(
                        key = it,
                        updater = { null },
                        get = { null },
                        persist = { null },
                    )
                }
            }
        }
    }
}

class MapDeleteByRoomIdRepositoryCoroutineCache<K1, K2, V>(
    private val repository: MapDeleteByRoomIdRepository<K1, K2, V>,
    private val tm: TransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    keyMapper: (MapRepositoryCoroutinesCacheKey<K1, K2>) -> RoomId,
) : MapRepositoryCoroutineCache<K1, K2, V>(
    repository = repository,
    tm = tm,
    cacheScope = cacheScope,
    expireDuration = expireDuration,
) {
    private val roomIdIndex: DeleteByRoomIdRepositoryCoroutineCacheValuesIndex<MapRepositoryCoroutinesCacheKey<K1, K2>> =
        DeleteByRoomIdRepositoryCoroutineCacheValuesIndex(keyMapper)

    init {
        addIndex(roomIdIndex)
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        coroutineScope {
            launch {
                tm.writeOperation { repository.deleteByRoomId(roomId) }
            }
            launch {
                roomIdIndex.getMapping(roomId).forEach {
                    updateAndGet(
                        key = it,
                        updater = { null },
                        get = { null },
                        persist = { null },
                    )
                }
            }
        }
    }
}