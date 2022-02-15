package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
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
    private val roomStateRepository: RoomStateRepository,
    private val rtm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val roomStateCache = TwoDimensionsRepositoryStateFlowCache(storeScope, roomStateRepository, rtm)

    suspend fun deleteAll() {
        rtm.transaction {
            roomStateRepository.deleteAll()
        }
        roomStateCache.reset()
    }

    private fun <C : StateEventContent> findType(eventContentClass: KClass<C>): String {
        return contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find state event, because it is not supported. You need to register it first.")
    }

    suspend fun update(event: Event<out StateEventContent>, skipWhenAlreadyPresent: Boolean = false) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val eventType = when (val content = event.content) {
                is UnknownStateEventContent -> content.eventType
                is RedactedStateEventContent -> content.eventType
                else -> contentMappings.state.find { it.kClass.isInstance(event.content) }?.type
            }
                ?: throw IllegalArgumentException("Cannot find state event, because it is not supported. You need to register it first.")
            roomStateCache.updateBySecondKey(RoomStateRepositoryKey(roomId, eventType), stateKey) {
                if (skipWhenAlreadyPresent && it != null) it else event
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Map<String, Event<C>?>?> {
        val eventType = findType(eventContentClass)
        return roomStateCache.get(RoomStateRepositoryKey(roomId, eventType), scope = scope)
            .mapLatest { value ->
                value?.mapValues {
                    if (it.value.content.instanceOf(eventContentClass)) {
                        @Suppress("UNCHECKED_CAST")
                        it.value as Event<C>
                    } else null
                }
            }.stateIn(scope)
    }

    suspend fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>
    ): Map<String, Event<C>?>? {
        val eventType = findType(eventContentClass)
        return roomStateCache.get(RoomStateRepositoryKey(roomId, eventType))
            ?.mapValues {
                if (it.value.content.instanceOf(eventContentClass)) {
                    @Suppress("UNCHECKED_CAST")
                    it.value as Event<C>
                } else null
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Event<C>?> {
        val eventType = findType(eventContentClass)
        return roomStateCache.getBySecondKey(RoomStateRepositoryKey(roomId, eventType), stateKey, scope)
            .transformLatest { if (it?.content?.instanceOf(eventContentClass) == true) emit(it) else emit(null) }
            .filterIsInstance<Event<C>?>()
            .stateIn(scope)
    }

    suspend fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
    ): Event<C>? {
        val eventType = findType(eventContentClass)
        val value = roomStateCache.getBySecondKey(RoomStateRepositoryKey(roomId, eventType), stateKey)
        return if (value?.content?.instanceOf(eventContentClass) == true) {
            @Suppress("UNCHECKED_CAST")
            value as Event<C>?
        } else null
    }
}