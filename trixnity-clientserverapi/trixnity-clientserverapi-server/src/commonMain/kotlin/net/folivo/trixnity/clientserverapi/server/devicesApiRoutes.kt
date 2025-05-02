package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

@OptIn(MSC3814::class)
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
    matrixEndpoint(json, contentMappings, handler::getDehydratedDevice)
    matrixEndpoint(json, contentMappings, handler::setDehydratedDevice)
    matrixEndpoint(json, contentMappings, handler::deleteDehydratedDevice)
    matrixEndpoint(json, contentMappings, handler::getDehydratedDeviceEvents)
}