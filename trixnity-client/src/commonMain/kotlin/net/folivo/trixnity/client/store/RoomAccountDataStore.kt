package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryCoroutineCache
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomAccountDataStore(
    private val roomAccountDataRepository: RoomAccountDataRepository,
    private val tm: TransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
) : Store {
    private val roomAccountDataCache =
        MapDeleteByRoomIdRepositoryCoroutineCache(
            roomAccountDataRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.roomAccountData
        ) { it.firstKey.roomId }

    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        roomAccountDataCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        roomAccountDataCache.deleteByRoomId(roomId)
    }

    suspend fun save(event: RoomAccountDataEvent<*>) {
        val eventType = when (val content = event.content) {
            is UnknownRoomAccountDataEventContent -> content.eventType
            else -> contentMappings.roomAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        roomAccountDataCache.write(
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
        return roomAccountDataCache.read(
            MapRepositoryCoroutinesCacheKey(
                RoomAccountDataRepositoryKey(
                    roomId,
                    eventType
                ), key
            )
        )
            .map { if (it?.content?.instanceOf(eventContentClass) == true) it else null }
            .filterIsInstance()
    }
}
