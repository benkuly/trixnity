package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.pushApiRoutes(
    handler: PushApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::getPushers)
    matrixEndpoint(json, contentMappings, handler::setPushers)
    matrixEndpoint(json, contentMappings, handler::getNotifications)
    matrixEndpoint(json, contentMappings, handler::getPushRules)
    matrixEndpoint(json, contentMappings, handler::getPushRule)
    matrixEndpoint(json, contentMappings, handler::setPushRule)
    matrixEndpoint(json, contentMappings, handler::deletePushRule)
    matrixEndpoint(json, contentMappings, handler::getPushRuleActions)
    matrixEndpoint(json, contentMappings, handler::setPushRuleActions)
    matrixEndpoint(json, contentMappings, handler::getPushRuleEnabled)
    matrixEndpoint(json, contentMappings, handler::setPushRuleEnabled)
}