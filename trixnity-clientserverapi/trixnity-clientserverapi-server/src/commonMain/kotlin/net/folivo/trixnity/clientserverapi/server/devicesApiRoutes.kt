package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.devicesApiRoutes(
    handler: DevicesApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::getDevices)
    matrixEndpoint(json, contentMappings, handler::getDevice)
    matrixEndpoint(json, contentMappings, handler::updateDevice)
    matrixUIAEndpoint(json, contentMappings, handler::deleteDevices)
    matrixUIAEndpoint(json, contentMappings, handler::deleteDevice)
}