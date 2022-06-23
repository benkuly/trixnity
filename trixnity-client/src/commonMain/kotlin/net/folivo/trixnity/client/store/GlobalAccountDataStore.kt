package net.folivo.trixnity.client.store

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.transformLatest
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class GlobalAccountDataStore(
    private val globalAccountDataRepository: GlobalAccountDataRepository,
    private val rtm: RepositoryTransactionManager,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val globalAccountDataCache =
        TwoDimensionsRepositoryStateFlowCache(storeScope, globalAccountDataRepository, rtm)

    suspend fun deleteAll() {
        rtm.transaction {
            globalAccountDataRepository.deleteAll()
        }
        globalAccountDataCache.reset()
    }

    suspend fun update(event: GlobalAccountDataEvent<out GlobalAccountDataEventContent>) {
        val eventType = when (val content = event.content) {
            is UnknownGlobalAccountDataEventContent -> content.eventType
            else -> contentMappings.globalAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
            ?: throw IllegalArgumentException("Cannot update account data event $event, because it is not supported. You need to register it first.")
        globalAccountDataCache.updateBySecondKey(eventType, event.key) { event }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <C : GlobalAccountDataEventContent> get(
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): Flow<GlobalAccountDataEvent<C>?> {
        val eventType = contentMappings.globalAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event $eventContentClass, because it is not supported. You need to register it first.")
        return globalAccountDataCache.getBySecondKey(eventType, key, scope)
            .transformLatest { if (it?.content?.instanceOf(eventContentClass) == true) emit(it) else emit(null) }
            .filterIsInstance()
    }

    suspend fun <C : GlobalAccountDataEventContent> get(
        eventContentClass: KClass<C>,
        key: String = ""
    ): GlobalAccountDataEvent<C>? {
        val eventType = contentMappings.globalAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot find account data event $eventContentClass, because it is not supported. You need to register it first.")
        val value = globalAccountDataCache.getBySecondKey(eventType, key)
        return if (value?.content?.instanceOf(eventContentClass) == true) {
            @Suppress("UNCHECKED_CAST")
            value as GlobalAccountDataEvent<C>?
        } else null
    }
}
