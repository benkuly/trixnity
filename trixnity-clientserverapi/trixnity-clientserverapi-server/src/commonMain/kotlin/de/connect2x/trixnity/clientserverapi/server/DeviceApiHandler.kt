package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.device.*
import de.connect2x.trixnity.clientserverapi.model.uia.RequestWithUIA
import de.connect2x.trixnity.clientserverapi.model.uia.ResponseWithUIA
import de.connect2x.trixnity.core.MSC3814

interface DeviceApiHandler {
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