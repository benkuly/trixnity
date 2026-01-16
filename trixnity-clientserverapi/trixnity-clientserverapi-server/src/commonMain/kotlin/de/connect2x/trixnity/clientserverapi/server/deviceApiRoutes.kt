package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.deviceApiRoutes(
    handler: DeviceApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::getDevices)
    matrixEndpoint(json, contentMappings, handler::getDevice)
    matrixEndpoint(json, contentMappings, handler::updateDevice)
    matrixUIAEndpoint(json, contentMappings, handler::deleteDevices)
    matrixUIAEndpoint(json, contentMappings, handler::deleteDevice)
    @OptIn(MSC3814::class)
    matrixEndpoint(json, contentMappings, handler::getDehydratedDevice)
    @OptIn(MSC3814::class)
    matrixEndpoint(json, contentMappings, handler::setDehydratedDevice)
    @OptIn(MSC3814::class)
    matrixEndpoint(json, contentMappings, handler::deleteDehydratedDevice)
    @OptIn(MSC3814::class)
    matrixEndpoint(json, contentMappings, handler::getDehydratedDeviceEvents)
}