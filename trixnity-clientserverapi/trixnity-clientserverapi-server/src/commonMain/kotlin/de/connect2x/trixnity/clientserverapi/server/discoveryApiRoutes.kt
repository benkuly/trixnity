package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.discoveryApiRoutes(
    handler: DiscoveryApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::getWellKnown)
    matrixEndpoint(json, contentMappings, handler::getSupport)
}