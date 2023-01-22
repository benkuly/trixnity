package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.transformLatest
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownRoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomAccountDataStore(
    private val roomAccountDataRepository: RoomAccountDataRepository,
    private val tm: TransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
) : Store {
    private val roomAccountDataCache =
        TwoDimensionsRepositoryStateFlowCache(
            storeScope,
            roomAccountDataRepository,
            tm,
            config.cacheExpireDurations.roomAccountData
        )

    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        tm.writeOperation {
            roomAccountDataRepository.deleteAll()
        }
        roomAccountDataCache.reset()
    }

    suspend fun save(event: RoomAccountDataEvent<RoomAccountDataEventContent>) {
        val eventType = when (val content = event.content) {
            is UnknownRoomAccountDataEventContent -> content.eventType
            else -> contentMappings.roomAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        roomAccountDataCache.saveBySecondKey(
            RoomAccountDataRepositoryKey(event.roomId, eventType), event.key, event
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <C : RoomAccountDataEventContent> get(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<RoomAccountDataEvent<C>?> {
        val eventType = contentMappings.roomAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event, because it is not supported. You need to register it first.")
        return roomAccountDataCache.getBySecondKey(RoomAccountDataRepositoryKey(roomId, eventType), key)
            .transformLatest { if (it?.content?.instanceOf(eventContentClass) == true) emit(it) else emit(null) }
            .filterIsInstance()
    }
}
