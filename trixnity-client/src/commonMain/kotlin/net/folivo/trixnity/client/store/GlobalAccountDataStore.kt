package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.cache.MapRepositoryObservableCache
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class GlobalAccountDataStore(
    globalAccountDataRepository: GlobalAccountDataRepository,
    tm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
) : Store {
    private val globalAccountDataCache =
        MapRepositoryObservableCache(
            globalAccountDataRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.globalAccountDate
        )

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        globalAccountDataCache.deleteAll()
    }

    suspend fun save(event: GlobalAccountDataEvent<out GlobalAccountDataEventContent>) {
        val eventType = when (val content = event.content) {
            is UnknownEventContent -> content.eventType
            else -> contentMappings.globalAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
            ?: throw IllegalArgumentException("Cannot save account data event $event, because it is not supported. You need to register it first.")
        globalAccountDataCache.write(MapRepositoryCoroutinesCacheKey(eventType, event.key), event)
    }

    fun <C : GlobalAccountDataEventContent> get(
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<GlobalAccountDataEvent<C>?> {
        val eventType = contentMappings.globalAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event $eventContentClass, because it is not supported. You need to register it first.")
        return globalAccountDataCache.read(MapRepositoryCoroutinesCacheKey(eventType, key))
            .map { if (it?.content?.instanceOf(eventContentClass) == true) it else null }
            .filterIsInstance()
    }
}
