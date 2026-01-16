package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.userApiRoutes(
    handler: UserApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    matrixEndpoint(json, contentMappings, handler::getProfileField)
    matrixEndpoint(json, contentMappings, handler::setProfileField)
    matrixEndpoint(json, contentMappings, handler::deleteProfileField)
    matrixEndpoint(json, contentMappings, handler::getProfile)
    matrixEndpoint(json, contentMappings, handler::getPresence)
    matrixEndpoint(json, contentMappings, handler::setPresence)
    matrixEndpoint(json, contentMappings, handler::sendToDevice)
    matrixEndpoint(json, contentMappings, handler::getFilter)
    matrixEndpoint(json, contentMappings, handler::setFilter)
    matrixEndpoint(json, contentMappings, handler::getAccountData)
    matrixEndpoint(json, contentMappings, handler::setAccountData)
    matrixEndpoint(json, contentMappings, handler::searchUsers)
    matrixEndpoint(json, contentMappings, handler::reportUser)
}