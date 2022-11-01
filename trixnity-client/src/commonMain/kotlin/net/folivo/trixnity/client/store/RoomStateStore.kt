package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
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
    private val roomStateRepository: RoomStateRepository,
    private val rtm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) : Store {
    private val roomStateCache = TwoDimensionsRepositoryStateFlowCache(storeScope, roomStateRepository, rtm)

    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        rtm.writeTransaction {
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
    fun <C : StateEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Event<C>?>?> {
        val eventType = findType(eventContentClass)
        return roomStateCache.get(RoomStateRepositoryKey(roomId, eventType))
            .mapLatest { value ->
                value?.mapValues {
                    if (it.value.content.instanceOf(eventContentClass)) {
                        @Suppress("UNCHECKED_CAST")
                        it.value as Event<C>
                    } else null
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <C : StateEventContent> getByStateKey(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
    ): Flow<Event<C>?> {
        val eventType = findType(eventContentClass)
        return roomStateCache.getBySecondKey(RoomStateRepositoryKey(roomId, eventType), stateKey)
            .transformLatest { if (it?.content?.instanceOf(eventContentClass) == true) emit(it) else emit(null) }
            .filterIsInstance()
    }
}