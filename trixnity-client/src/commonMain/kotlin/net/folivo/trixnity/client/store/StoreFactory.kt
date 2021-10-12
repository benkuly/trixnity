package net.folivo.trixnity.client.store

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import kotlin.coroutines.CoroutineContext

interface StoreFactory {
    suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
        storeCoroutineContext: CoroutineContext = Dispatchers.Default,
        loggerFactory: LoggerFactory
    ): Store
}