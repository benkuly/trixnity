package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomStateStore(
    roomStateRepository: RoomStateRepository,
    tm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
) : Store {
    private val roomStateCache = MapDeleteByRoomIdRepositoryObservableCache(
        roomStateRepository,
        tm,
        storeScope,
        config.cacheExpireDurations.roomState
    ) { it.firstKey.roomId }

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        roomStateCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        roomStateCache.deleteByRoomId(roomId)
    }

    private fun <C : StateEventContent> findType(eventContentClass: KClass<C>): String {
        return contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find state event, because it is not supported. You need to register it first.")
    }

    suspend fun save(event: Event<out StateEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val eventType = when (val content = event.content) {
                is UnknownStateEventContent -> content.eventType
                is RedactedStateEventContent -> content.eventType
                else -> contentMappings.state.find { it.kClass.isInstance(event.content) }?.type
            }
                ?: throw IllegalArgumentException("Cannot find state event, because it is not supported. You need to register it first.")
            roomStateCache.write(
                MapRepositoryCoroutinesCacheKey(
                    RoomStateRepositoryKey(roomId, eventType),
                    stateKey
                ), event
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Flow<Event<C>?>>?> {
        val eventType = findType(eventContentClass)
        return roomStateCache.readByFirstKey(RoomStateRepositoryKey(roomId, eventType))
            .mapLatest { value ->
                value?.mapValues { entry ->
                    entry.value.map {
                        if (it?.content?.instanceOf(eventContentClass) == true) {
                            @Suppress("UNCHECKED_CAST")
                            it as Event<C>
                        } else null
                    }
                }
            }
    }

    fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String,
    ): Flow<Event<C>?> {
        val eventType = findType(eventContentClass)
        return roomStateCache.read(MapRepositoryCoroutinesCacheKey(RoomStateRepositoryKey(roomId, eventType), stateKey))
            .map { if (it?.content?.instanceOf(eventContentClass) == true) it else null }
            .filterIsInstance()
    }
}