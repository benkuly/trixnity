package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.syncApiRoutes(
    handler: SyncApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    matrixEndpoint(json, contentMappings, handler::sync)
}