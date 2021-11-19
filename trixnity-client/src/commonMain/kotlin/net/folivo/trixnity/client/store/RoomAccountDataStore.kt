package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomAccountDataStore(
    roomAccountDataRepository: RoomAccountDataRepository,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val roomAccountDataCache = StateFlowCache(storeScope, roomAccountDataRepository)

    suspend fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<RoomAccountDataEvent<C>?> {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot get account data event, because it is not supported. You need to register it first.")
        val key = RoomAccountDataRepositoryKey(roomId, eventType)
        @Suppress("UNCHECKED_CAST")
        return roomAccountDataCache.get(key, scope) as StateFlow<RoomAccountDataEvent<C>>
    }

    suspend fun update(event: RoomAccountDataEvent<out RoomAccountDataEventContent>) {
        val eventType = when (val content = event.content) {
            is UnknownRoomAccountDataEventContent -> content.eventType
            else -> contentMappings.roomAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
        requireNotNull(eventType)
        roomAccountDataCache.update(RoomAccountDataRepositoryKey(event.roomId, eventType)) { event }
    }
}
