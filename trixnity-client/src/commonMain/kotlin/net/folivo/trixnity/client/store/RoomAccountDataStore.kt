package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.AccountDataEventContent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnknownAccountDataEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomAccountDataStore(
    roomAccountDataRepository: RoomAccountDataRepository,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val roomAccountDataCache = StateFlowCache(storeScope, roomAccountDataRepository)

    suspend fun <C : AccountDataEventContent> get(
        roomId: MatrixId.RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Event.AccountDataEvent<C>?> {
        val eventType = contentMappings.accountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot get account data event, because it is not supported. You need to register it first.")
        val key = RoomAccountDataRepositoryKey(roomId, eventType)
        @Suppress("UNCHECKED_CAST")
        return roomAccountDataCache.get(key, scope) as StateFlow<Event.AccountDataEvent<C>>
    }

    suspend fun update(event: Event.AccountDataEvent<out AccountDataEventContent>) {
        val roomId = event.roomId

        if (roomId != null) {
            val eventType = when (val content = event.content) {
                is UnknownAccountDataEventContent -> content.eventType
                else -> contentMappings.accountData.find { it.kClass.isInstance(event.content) }?.type
            }
            requireNotNull(eventType)
            roomAccountDataCache.update(RoomAccountDataRepositoryKey(roomId, eventType)) {
                event
            }
        }
    }
}
