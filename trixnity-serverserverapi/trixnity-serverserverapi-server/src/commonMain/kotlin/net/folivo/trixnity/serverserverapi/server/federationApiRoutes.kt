package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.federationApiRoutes(
    handler: FederationApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint(json, contentMappings, handler::sendTransaction)
        matrixEndpoint(json, contentMappings, handler::getEventAuthChain)
        matrixEndpoint(json, contentMappings, handler::backfillRoom)
    }
}