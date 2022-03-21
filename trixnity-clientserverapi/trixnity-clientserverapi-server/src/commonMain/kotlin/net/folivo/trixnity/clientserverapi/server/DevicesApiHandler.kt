package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA

interface DevicesApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3devices">matrix spec</a>
     */
    suspend fun getDevices(context: MatrixEndpointContext<GetDevices, Unit, GetDevices.Response>): GetDevices.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun getDevice(context: MatrixEndpointContext<GetDevice, Unit, Device>): Device

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun updateDevice(context: MatrixEndpointContext<UpdateDevice, UpdateDevice.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3delete_devices">matrix spec</a>
     */
    suspend fun deleteDevices(context: MatrixEndpointContext<DeleteDevices, RequestWithUIA<DeleteDevices.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun deleteDevice(context: MatrixEndpointContext<DeleteDevice, RequestWithUIA<Unit>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>
}