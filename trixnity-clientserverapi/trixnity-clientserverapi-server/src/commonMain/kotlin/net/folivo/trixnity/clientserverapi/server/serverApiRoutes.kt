package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.serverApiRoutes(
    handler: ServerApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    matrixEndpoint(json, contentMappings, handler::getVersions)
    matrixEndpoint(json, contentMappings, handler::getCapabilities)
    matrixEndpoint(json, contentMappings, handler::search)
    matrixEndpoint(json, contentMappings, handler::whoIs)
}