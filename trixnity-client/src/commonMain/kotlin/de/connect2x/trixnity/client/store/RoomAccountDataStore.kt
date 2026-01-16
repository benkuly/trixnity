package de.connect2x.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepository
import de.connect2x.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass
import kotlin.time.Clock

class RoomAccountDataStore(
    roomAccountDataRepository: RoomAccountDataRepository,
    tm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomAccountDataCache =
        MapDeleteByRoomIdRepositoryObservableCache(
            roomAccountDataRepository,
            tm,
            storeScope,
            clock,
            config.cacheExpireDurations.roomAccountData
        ) { it.firstKey.roomId }.also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        roomAccountDataCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        roomAccountDataCache.deleteByRoomId(roomId)
    }

    suspend fun save(event: RoomAccountDataEvent<*>) {
        val eventType = when (val content = event.content) {
            is UnknownEventContent -> content.eventType
            else -> contentMappings.roomAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        roomAccountDataCache.set(
            MapRepositoryCoroutinesCacheKey(RoomAccountDataRepositoryKey(event.roomId, eventType), event.key), event
        )
    }

    fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<RoomAccountDataEvent<C>?> {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        @Suppress("UNCHECKED_CAST")
        return roomAccountDataCache.get(
            MapRepositoryCoroutinesCacheKey(RoomAccountDataRepositoryKey(roomId, eventType), key)
        ).map { if (it?.content?.instanceOf(eventContentClass) == true) it else null }
                as Flow<RoomAccountDataEvent<C>?>
    }
}
