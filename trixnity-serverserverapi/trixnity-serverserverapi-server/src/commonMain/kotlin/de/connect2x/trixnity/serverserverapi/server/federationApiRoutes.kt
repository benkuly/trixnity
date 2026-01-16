package de.connect2x.trixnity.serverserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.federationApiRoutes(
    handler: FederationApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
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
    matrixEndpoint(json, contentMappings, handler::makeLeave)
    matrixEndpoint(json, contentMappings, handler::sendLeave)
    matrixEndpoint(json, contentMappings, handler::onBindThirdPid)
    matrixEndpoint(json, contentMappings, handler::exchangeThirdPartyInvite)
    matrixEndpoint(json, contentMappings, handler::getPublicRooms)
    matrixEndpoint(json, contentMappings, handler::getPublicRoomsWithFilter)
    matrixEndpoint(json, contentMappings, handler::getHierarchy)
    matrixEndpoint(json, contentMappings, handler::queryDirectory)
    matrixEndpoint(json, contentMappings, handler::queryProfile)
    matrixEndpoint(json, contentMappings, handler::getOIDCUserInfo)
    matrixEndpoint(json, contentMappings, handler::getDevices)
    matrixEndpoint(json, contentMappings, handler::claimKeys)
    matrixEndpoint(json, contentMappings, handler::getKeys)
    matrixEndpoint(json, contentMappings, handler::timestampToEvent)
    matrixEndpoint(json, contentMappings, handler::downloadMedia)
    matrixEndpoint(json, contentMappings, handler::downloadThumbnail)
}