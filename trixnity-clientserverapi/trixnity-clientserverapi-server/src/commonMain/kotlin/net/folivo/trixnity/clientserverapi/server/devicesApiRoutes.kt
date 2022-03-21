package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.devicesApiRoutes(
    handler: DevicesApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<GetDevices, GetDevices.Response>(json, contentMappings) {
            handler.getDevices(this)
        }
        matrixEndpoint<GetDevice, Device>(json, contentMappings) {
            handler.getDevice(this)
        }
        matrixEndpoint<UpdateDevice, UpdateDevice.Request>(json, contentMappings) {
            handler.updateDevice(this)
        }
        matrixUIAEndpoint<DeleteDevices, DeleteDevices.Request, Unit>(json, contentMappings) {
            handler.deleteDevices(this)
        }
        matrixUIAEndpoint<DeleteDevice, Unit, Unit>(json, contentMappings) {
            handler.deleteDevice(this)
        }
    }
}