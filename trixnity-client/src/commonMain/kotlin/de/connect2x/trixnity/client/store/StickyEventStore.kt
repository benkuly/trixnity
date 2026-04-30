package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryObservableCache
import de.connect2x.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepositoryFirstKey
import de.connect2x.trixnity.client.store.repository.StickyEventRepositorySecondKey
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.reflect.KClass
import kotlin.time.Clock

@MSC4354
class StickyEventStore(
    private val stickyEventRepository: StickyEventRepository,
    private val tm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    private val clock: Clock,
) : Store {

    private val stickyEventCache = MapDeleteByRoomIdRepositoryObservableCache(
        stickyEventRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.stickyEvent,
    ) { it.firstKey.roomId }.also(statisticCollector::addCache)

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        stickyEventCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        stickyEventCache.deleteByRoomId(roomId)
    }

    suspend fun deleteByEventId(roomId: RoomId, eventId: EventId) {
        val key = tm.readTransaction { stickyEventRepository.getByEventId(roomId, eventId) }
        if (key != null) {
            stickyEventCache.set(MapRepositoryCoroutinesCacheKey(key.first, key.second), null)
        }
    }

    suspend fun save(storedStickyEvent: StoredStickyEvent<StickyEventContent>) {
        val event = storedStickyEvent.event
        if (event.sticky == null) return
        val eventType = when (val content = event.content) {
            is UnknownEventContent -> content.eventType
            else -> findType(event.content::class)
        }
        stickyEventCache.update(
            MapRepositoryCoroutinesCacheKey(
                StickyEventRepositoryFirstKey(event.roomId, eventType),
                StickyEventRepositorySecondKey(event.sender, event.content.stickyKey),
            )
        ) {
            when {
                it == null -> storedStickyEvent
                it.endTime == storedStickyEvent.endTime ->
                    if (it.event.id.full > storedStickyEvent.event.id.full) it
                    else storedStickyEvent

                it.endTime > storedStickyEvent.endTime -> it
                else -> storedStickyEvent
            }
        }
    }

    suspend fun deleteInvalid() {
        val now = clock.now()
        val invalidEvents = tm.readTransaction {
            stickyEventRepository.getByEndTimeBefore(now)
        }
        for (invalidEvent in invalidEvents) {
            stickyEventCache.update(MapRepositoryCoroutinesCacheKey(invalidEvent.first, invalidEvent.second)) {
                if (it == null || it.endTime < now) null
                else it
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <C : StickyEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<Pair<UserId, String?>, Flow<StoredStickyEvent<C>?>>> {
        val eventType = findType(eventContentClass)
        return stickyEventCache.readByFirstKey(StickyEventRepositoryFirstKey(roomId, eventType))
            .map { value ->
                value.mapValues { entry ->
                    entry.value.filterIsContent(eventContentClass).filterValid()
                }.mapKeys { it.key.sender to it.key.stickyKey }
            }
    }

    fun <C : StickyEventContent> getBySenderAndStickyKey(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        sender: UserId,
        stickyKey: String?,
    ): Flow<StoredStickyEvent<C>?> {
        val eventType = findType(eventContentClass)
        return stickyEventCache.get(
            MapRepositoryCoroutinesCacheKey(
                StickyEventRepositoryFirstKey(roomId, eventType),
                StickyEventRepositorySecondKey(sender, stickyKey)
            )
        ).filterIsContent(eventContentClass).filterValid()
    }

    private fun <C : StickyEventContent> Flow<StoredStickyEvent<*>?>.filterIsContent(eventContentClass: KClass<C>) =
        map {
            val event = it?.event
            if (event?.content?.instanceOf(eventContentClass) == true) it
            else null
        }.let {
            @Suppress("UNCHECKED_CAST")
            it as Flow<StoredStickyEvent<C>?>
        }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <C : StickyEventContent> Flow<StoredStickyEvent<C>?>.filterValid(): Flow<StoredStickyEvent<C>?> =
        transformLatest {
            if (it == null) {
                emit(null)
                return@transformLatest
            }
            val now = clock.now()
            if (it.endTime < now) {
                emit(null)
                return@transformLatest
            }
            emit(it)
            delay(it.endTime - now)
            emit(null)
        }


    private fun <C : RoomEventContent> findType(eventContentClass: KClass<C>): String {
        return contentMappings.message.find { it.kClass == eventContentClass }?.type
            ?: contentMappings.state.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find sticky event type, because it is not supported. You need to register it first.")
    }
}


@OptIn(MSC4354::class)
inline fun <reified C : StickyEventContent> StickyEventStore.get(
    roomId: RoomId,
): Flow<Map<Pair<UserId, String?>, Flow<StoredStickyEvent<C>?>>> =
    get(
        roomId = roomId,
        eventContentClass = C::class
    )

@OptIn(MSC4354::class)
inline fun <reified C : StickyEventContent> StickyEventStore.getBySenderAndStickyKey(
    roomId: RoomId,
    sender: UserId,
    stateKey: String? = null,
): Flow<StoredStickyEvent<C>?> =
    getBySenderAndStickyKey(
        roomId = roomId,
        eventContentClass = C::class,
        sender = sender,
        stickyKey = stateKey
    )

