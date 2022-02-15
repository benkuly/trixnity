package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): StateFlow<RoomAccountDataEvent<C>?> {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        return roomAccountDataCache.getBySecondKey(RoomAccountDataRepositoryKey(roomId, eventType), key, scope)
            .transformLatest { if (it?.content?.instanceOf(eventContentClass) == true) emit(it) else emit(null) }
            .filterIsInstance<RoomAccountDataEvent<C>?>()
            .stateIn(scope)
    }

    suspend fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): RoomAccountDataEvent<C>? {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        val value = roomAccountDataCache.getBySecondKey(RoomAccountDataRepositoryKey(roomId, eventType), key)
        return if (value?.content?.instanceOf(eventContentClass) == true) {
            @Suppress("UNCHECKED_CAST")
            value as RoomAccountDataEvent<C>?
        } else null
    }
}
