package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass
import kotlin.time.Clock

class RoomStateStore(
    private val roomStateRepository: RoomStateRepository,
    private val tm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    private val roomStateCache = MapDeleteByRoomIdRepositoryObservableCache(
        roomStateRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.roomState
    ) { it.firstKey.roomId }.also(statisticCollector::addCache)

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

    suspend fun save(event: StateBaseEvent<*>, skipWhenAlreadyPresent: Boolean = false) {
        val roomId = event.roomId
        val stateKey = event.stateKey
        if (roomId != null) {
            val eventType = when (val content = event.content) {
                is UnknownEventContent -> content.eventType
                is RedactedEventContent -> content.eventType
                else -> contentMappings.state.find { it.kClass.isInstance(event.content) }?.type
            }
                ?: throw IllegalArgumentException("Cannot find state event, because it is not supported. You need to register it first.")
            if (skipWhenAlreadyPresent)
                roomStateCache.update(
                    MapRepositoryCoroutinesCacheKey(
                        RoomStateRepositoryKey(roomId, eventType),
                        stateKey
                    )
                ) {
                    it ?: event
                }
            else
                roomStateCache.set(
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
    ): Flow<Map<String, Flow<StateBaseEvent<C>?>>> {
        val eventType = findType(eventContentClass)
        return roomStateCache.readByFirstKey(RoomStateRepositoryKey(roomId, eventType))
            .mapLatest { value ->
                value.mapValues { entry ->
                    entry.value.map {
                        if (it?.content?.instanceOf(eventContentClass) == true) {
                            @Suppress("UNCHECKED_CAST")
                            it as StateBaseEvent<C>
                        } else null
                    }
                }
            }
    }

    fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String,
    ): Flow<StateBaseEvent<C>?> {
        val eventType = findType(eventContentClass)
        @Suppress("UNCHECKED_CAST")
        return roomStateCache.get(MapRepositoryCoroutinesCacheKey(RoomStateRepositoryKey(roomId, eventType), stateKey))
            .map { if (it?.content?.instanceOf(eventContentClass) == true) it else null }
                as Flow<StateBaseEvent<C>?>
    }

    suspend fun <C : StateEventContent> getByRooms(
        roomIds: Set<RoomId>,
        eventContentClass: KClass<C>,
        stateKey: String,
    ): List<StateBaseEvent<C>> {
        val eventType = findType(eventContentClass)
        @Suppress("UNCHECKED_CAST")
        return tm.readTransaction { roomStateRepository.getByRooms(roomIds, eventType, stateKey) }
            .mapNotNull { if (it.content.instanceOf(eventContentClass)) it else null }
            .filterIsInstance<StateBaseEvent<C>>()
    }
}