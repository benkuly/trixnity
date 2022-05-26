package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA

interface DevicesApiHandler {
    /**
     * @see [GetDevices]
     */
    suspend fun getDevices(context: MatrixEndpointContext<GetDevices, Unit, GetDevices.Response>): GetDevices.Response

    /**
     * @see [GetDevice]
     */
    suspend fun getDevice(context: MatrixEndpointContext<GetDevice, Unit, Device>): Device

    /**
     * @see [UpdateDevice]
     */
    suspend fun updateDevice(context: MatrixEndpointContext<UpdateDevice, UpdateDevice.Request, Unit>)

    /**
     * @see [DeleteDevices]
     */
    suspend fun deleteDevices(context: MatrixEndpointContext<DeleteDevices, RequestWithUIA<DeleteDevices.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see [DeleteDevice]
     */
    suspend fun deleteDevice(context: MatrixEndpointContext<DeleteDevice, RequestWithUIA<Unit>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>
}