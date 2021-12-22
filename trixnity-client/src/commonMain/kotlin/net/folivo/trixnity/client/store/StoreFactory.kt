package net.folivo.trixnity.client.store

import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings

interface StoreFactory {
    suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json
    ): Store
}