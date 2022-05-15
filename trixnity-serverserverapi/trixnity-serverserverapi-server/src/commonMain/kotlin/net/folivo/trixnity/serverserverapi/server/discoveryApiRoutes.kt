package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.discoveryApiRoutes(
    handler: DiscoveryApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint(json, contentMappings, handler::getWellKnown)
        matrixEndpoint(json, contentMappings, handler::getServerVersion)
        matrixEndpoint(json, contentMappings, handler::getServerKeys)
        matrixEndpoint(json, contentMappings, handler::queryServerKeys)
        matrixEndpoint(json, contentMappings, handler::queryKeysByServer)
    }
}