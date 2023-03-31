package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.roomsApiRoutes(
    handler: RoomsApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    matrixEndpoint(json, contentMappings, handler::getEvent)
    matrixEndpoint(json, contentMappings, handler::getStateEvent)
    matrixEndpoint(json, contentMappings, handler::getState)
    matrixEndpoint(json, contentMappings, handler::getMembers)
    matrixEndpoint(json, contentMappings, handler::getJoinedMembers)
    matrixEndpoint(json, contentMappings, handler::getEvents)
    matrixEndpoint(json, contentMappings, handler::getRelations)
    matrixEndpoint(json, contentMappings, handler::getRelationsByRelationType)
    matrixEndpoint(json, contentMappings, handler::getRelationsByRelationTypeAndEventType)
    matrixEndpoint(json, contentMappings, handler::getThreads)
    matrixEndpoint(json, contentMappings, handler::sendStateEvent)
    matrixEndpoint(json, contentMappings, handler::sendMessageEvent)
    matrixEndpoint(json, contentMappings, handler::redactEvent)
    matrixEndpoint(json, contentMappings, handler::createRoom)
    matrixEndpoint(json, contentMappings, handler::setRoomAlias)
    matrixEndpoint(json, contentMappings, handler::getRoomAlias)
    matrixEndpoint(json, contentMappings, handler::getRoomAliases)
    matrixEndpoint(json, contentMappings, handler::deleteRoomAlias)
    matrixEndpoint(json, contentMappings, handler::getJoinedRooms)
    matrixEndpoint(json, contentMappings, handler::inviteUser)
    matrixEndpoint(json, contentMappings, handler::kickUser)
    matrixEndpoint(json, contentMappings, handler::banUser)
    matrixEndpoint(json, contentMappings, handler::unbanUser)
    matrixEndpoint(json, contentMappings, handler::joinRoom)
    matrixEndpoint(json, contentMappings, handler::knockRoom)
    matrixEndpoint(json, contentMappings, handler::forgetRoom)
    matrixEndpoint(json, contentMappings, handler::leaveRoom)
    matrixEndpoint(json, contentMappings, handler::setReceipt)
    matrixEndpoint(json, contentMappings, handler::setReadMarkers)
    matrixEndpoint(json, contentMappings, handler::setTyping)
    matrixEndpoint(json, contentMappings, handler::getAccountData)
    matrixEndpoint(json, contentMappings, handler::setAccountData)
    matrixEndpoint(json, contentMappings, handler::getDirectoryVisibility)
    matrixEndpoint(json, contentMappings, handler::setDirectoryVisibility)
    matrixEndpoint(json, contentMappings, handler::getPublicRooms)
    matrixEndpoint(json, contentMappings, handler::getPublicRoomsWithFilter)
    matrixEndpoint(json, contentMappings, handler::getTags)
    matrixEndpoint(json, contentMappings, handler::setTag)
    matrixEndpoint(json, contentMappings, handler::deleteTag)
    matrixEndpoint(json, contentMappings, handler::getEventContext)
    matrixEndpoint(json, contentMappings, handler::reportEvent)
    matrixEndpoint(json, contentMappings, handler::upgradeRoom)
    matrixEndpoint(json, contentMappings, handler::getHierarchy)
    matrixEndpoint(json, contentMappings, handler::timestampToEvent)
}