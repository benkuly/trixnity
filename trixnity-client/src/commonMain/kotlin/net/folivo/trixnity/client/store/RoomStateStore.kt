package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomStateStore(
    roomStateRepository: RoomStateRepository,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val roomStateCache = StateFlowCache(storeScope, roomStateRepository)

    suspend fun update(event: Event<out StateEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val eventType = when (val content = event.content) {
                is UnknownStateEventContent -> content.eventType
                is RedactedStateEventContent -> content.eventType
                else -> contentMappings.state.find { it.kClass.isInstance(event.content) }?.type
            }
            requireNotNull(eventType)
            roomStateCache.writeWithCache(RoomStateRepositoryKey(roomId, eventType),
                updater = { it?.plus(stateKey to event) ?: mapOf(stateKey to event) },
                // We don't mind, what is stored in database, because we always override it.
                containsInCache = { true },
                getFromRepositoryAndUpdateCache = { _, _ -> null },
                persistIntoRepository = { _, repository ->
                    repository.saveByStateKey(RoomStateRepositoryKey(roomId, eventType), stateKey, event)
                })
        }
    }

    suspend fun updateAll(events: List<Event<out StateEventContent>>) {
        events.forEach { update(it) }
    }

    suspend fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Map<String, Event<C>>?> {
        val eventType = contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot get state event, because it is not supported. You need to register it first.")
        @Suppress("UNCHECKED_CAST")
        return roomStateCache.get(
            RoomStateRepositoryKey(roomId, eventType), scope
        ) as StateFlow<Map<String, Event<C>>?>
    }

    suspend fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>
    ): Map<String, Event<C>>? {
        val eventType = contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot get state event, because it is not supported. You need to register it first.")
        @Suppress("UNCHECKED_CAST")
        return roomStateCache.get(
            RoomStateRepositoryKey(roomId, eventType)
        ) as Map<String, Event<C>>?
    }

    private suspend fun <C : StateEventContent> getByStateKeyAsFlow(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
        scope: CoroutineScope? = null
    ): Flow<Event<C>?> {
        val eventType = contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot get state event, because it is not supported. You need to register it first.")
        return roomStateCache.readWithCache(
            RoomStateRepositoryKey(roomId, eventType),
            containsInCache = { it?.containsKey(stateKey) ?: false },
            retrieveFromRepoAndUpdateCache = { cacheValue, repo ->
                val newValue = repo.getByStateKey(RoomStateRepositoryKey(roomId, eventType), stateKey)
                if (newValue != null) cacheValue?.plus(stateKey to newValue) ?: mapOf(stateKey to newValue)
                else cacheValue
            },
            scope
        ).map {
            @Suppress("UNCHECKED_CAST")
            it?.get(stateKey) as Event<C>?
        }
    }

    suspend fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Event<C>?> {
        return getByStateKeyAsFlow(roomId, stateKey, eventContentClass, scope).stateIn(scope)
    }

    suspend fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
    ): Event<C>? {
        return getByStateKeyAsFlow(roomId, stateKey, eventContentClass).firstOrNull()
    }
}