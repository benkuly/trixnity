package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class GlobalAccountDataStore(
    globalAccountDataRepository: GlobalAccountDataRepository,
    private val contentMappings: EventContentSerializerMappings,
    storeScope: CoroutineScope,
) {
    private val globalAccountDataCache = StateFlowCache(storeScope, globalAccountDataRepository)

    suspend fun <C : GlobalAccountDataEventContent> get(
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<GlobalAccountDataEvent<C>?> {
        val eventType = contentMappings.globalAccountData.find { it.kClass == eventContentClass }?.type
            ?: throw IllegalArgumentException("Cannot get account data event, because it is not supported. You need to register it first.")
        @Suppress("UNCHECKED_CAST")
        return globalAccountDataCache.get(eventType, scope) as StateFlow<GlobalAccountDataEvent<C>>
    }

    suspend fun update(event: GlobalAccountDataEvent<out GlobalAccountDataEventContent>) {
        val eventType = when (val content = event.content) {
            is UnknownGlobalAccountDataEventContent -> content.eventType
            else -> contentMappings.globalAccountData.find { it.kClass.isInstance(event.content) }?.type
        }
        requireNotNull(eventType)
        globalAccountDataCache.update(eventType) { event }
    }
}
