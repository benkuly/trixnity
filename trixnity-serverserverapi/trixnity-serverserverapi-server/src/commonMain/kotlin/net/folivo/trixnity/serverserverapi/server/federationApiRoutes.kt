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
        matrixEndpoint(json, contentMappings, handler::getMissingEvents)
        matrixEndpoint(json, contentMappings, handler::getEvent)
        matrixEndpoint(json, contentMappings, handler::getState)
        matrixEndpoint(json, contentMappings, handler::getStateIds)
        matrixEndpoint(json, contentMappings, handler::makeJoin)
        matrixEndpoint(json, contentMappings, handler::sendJoin)
        matrixEndpoint(json, contentMappings, handler::makeKnock)
        matrixEndpoint(json, contentMappings, handler::sendKnock)
        matrixEndpoint(json, contentMappings, handler::invite)
    }
}