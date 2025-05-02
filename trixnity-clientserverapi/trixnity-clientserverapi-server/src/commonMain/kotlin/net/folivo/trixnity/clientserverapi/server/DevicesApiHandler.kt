package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.MSC3814

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

    /**
     * @see [GetDehydratedDevice]
     */
    @MSC3814
    suspend fun getDehydratedDevice(context: MatrixEndpointContext<GetDehydratedDevice, Unit, GetDehydratedDevice.Response>): GetDehydratedDevice.Response

    /**
     * @see [SetDehydratedDevice]
     */
    @MSC3814
    suspend fun setDehydratedDevice(context: MatrixEndpointContext<SetDehydratedDevice, SetDehydratedDevice.Request, SetDehydratedDevice.Response>): SetDehydratedDevice.Response

    /**
     * @see [DeleteDehydratedDevice]
     */
    @MSC3814
    suspend fun deleteDehydratedDevice(context: MatrixEndpointContext<DeleteDehydratedDevice, Unit, DeleteDehydratedDevice.Response>): DeleteDehydratedDevice.Response

    /**
     * @see [GetDehydratedDeviceEvents]
     */
    @MSC3814
    suspend fun getDehydratedDeviceEvents(context: MatrixEndpointContext<GetDehydratedDeviceEvents, GetDehydratedDeviceEvents.Request, GetDehydratedDeviceEvents.Response>): GetDehydratedDeviceEvents.Response
}