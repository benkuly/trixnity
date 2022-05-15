package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.usersApiRoutes(
    handler: UsersApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    authenticate {
        matrixEndpoint(json, contentMappings, handler::getDisplayName)
        matrixEndpoint(json, contentMappings, handler::setDisplayName)
        matrixEndpoint(json, contentMappings, handler::getAvatarUrl)
        matrixEndpoint(json, contentMappings, handler::setAvatarUrl)
        matrixEndpoint(json, contentMappings, handler::getProfile)
        matrixEndpoint(json, contentMappings, handler::getPresence)
        matrixEndpoint(json, contentMappings, handler::setPresence)
        matrixEndpoint(json, contentMappings, handler::sendToDevice)
        matrixEndpoint(json, contentMappings, handler::getFilter)
        matrixEndpoint(json, contentMappings, handler::setFilter)
        matrixEndpoint(json, contentMappings, handler::getAccountData)
        matrixEndpoint(json, contentMappings, handler::setAccountData)
        matrixEndpoint(json, contentMappings, handler::searchUsers)
    }
}