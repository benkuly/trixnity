package net.folivo.trixnity.client.api

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import net.folivo.trixnity.client.api.model.devices.DeleteDevicesRequest
import net.folivo.trixnity.client.api.model.devices.Device
import net.folivo.trixnity.client.api.model.devices.GetDevicesResponse
import net.folivo.trixnity.client.api.model.devices.UpdateDeviceRequest

class DevicesApiClient(
    private val httpClient: MatrixHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun getDevices(): Result<GetDevicesResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/devices")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3devices">matrix spec</a>
     */
    suspend fun getDevice(deviceId: String): Result<Device> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/devices/${deviceId}")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun updateDevice(
        deviceId: String,
        displayName: String,
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url("/_matrix/client/v3/devices/${deviceId}")
            body = UpdateDeviceRequest(displayName)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3delete_devices">matrix spec</a>
     */
    suspend fun deleteDevices(devices: List<String>): Result<UIA<Unit>> =
        httpClient.uiaRequest(
            body = DeleteDevicesRequest(devices)
        ) {
            method = Post
            url("/_matrix/client/v3/delete_devices")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#delete_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun deleteDevice(
        deviceId: String,
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest {
            method = Delete
            url("/_matrix/client/v3/devices/${deviceId}")
        }
}
