package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.syncApiRoutes(
    handler: SyncApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    matrixEndpoint(json, contentMappings, handler::sync)
}