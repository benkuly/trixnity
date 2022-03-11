package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.core.model.UserId

class DevicesApiClient(
    private val httpClient: MatrixClientServerApiHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3devices">matrix spec</a>
     */
    suspend fun getDevices(asUserId: UserId? = null): Result<List<Device>> =
        httpClient.request(GetDevices(asUserId)).map { it.devices }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun getDevice(deviceId: String, asUserId: UserId? = null): Result<Device> =
        httpClient.request(GetDevice(deviceId.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun updateDevice(
        deviceId: String,
        displayName: String,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request(UpdateDevice(deviceId.e(), asUserId), UpdateDevice.Request(displayName))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3delete_devices">matrix spec</a>
     */
    suspend fun deleteDevices(devices: List<String>, asUserId: UserId? = null): Result<UIA<Unit>> =
        httpClient.uiaRequest(DeleteDevices(asUserId), DeleteDevices.Request(devices))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun deleteDevice(deviceId: String, asUserId: UserId? = null): Result<UIA<Unit>> =
        httpClient.uiaRequest(DeleteDevice(deviceId.e(), asUserId))
}
