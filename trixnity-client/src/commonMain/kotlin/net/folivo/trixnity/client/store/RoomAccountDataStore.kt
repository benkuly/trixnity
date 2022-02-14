package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomAccountDataStore(
    private val roomAccountDataRepository: RoomAccountDataRepository,
    private val rtm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val roomAccountDataCache = TwoDimensionsRepositoryStateFlowCache(storeScope, roomAccountDataRepository, rtm)

    suspend fun deleteAll() {
        rtm.transaction {
            roomAccountDataRepository.deleteAll()
        }
        roomAccountDataCache.reset()
    }

    suspend fun update(event: RoomAccountDataEvent<RoomAccountDataEventContent>) {
        val eventType = when (val content = event.content) {
            is UnknownRoomAccountDataEventContent -> content.eventType
            else -> contentMappings.roomAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        roomAccountDataCache.updateBySecondKey(
            RoomAccountDataRepositoryKey(event.roomId, eventType), event.key
        ) { event }
    }

    suspend fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): RoomAccountDataEvent<C>? {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        val firstKey = RoomAccountDataRepositoryKey(roomId, eventType)
        @Suppress("UNCHECKED_CAST")
        return roomAccountDataCache.getBySecondKey(firstKey, key) as RoomAccountDataEvent<C>
    }

    suspend fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): StateFlow<RoomAccountDataEvent<C>?> {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        val firstKey = RoomAccountDataRepositoryKey(roomId, eventType)
        @Suppress("UNCHECKED_CAST")
        return roomAccountDataCache.getBySecondKey(firstKey, key, scope) as StateFlow<RoomAccountDataEvent<C>>
    }
}
