package net.folivo.trixnity.client.store

import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory

interface StoreFactory {
    suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
        loggerFactory: LoggerFactory
    ): Store
}