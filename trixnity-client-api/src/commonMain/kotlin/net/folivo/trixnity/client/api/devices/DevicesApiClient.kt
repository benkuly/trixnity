package net.folivo.trixnity.client.api.devices

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import net.folivo.trixnity.client.api.MatrixHttpClient
import net.folivo.trixnity.client.api.uia.UIA

class DevicesApiClient(
    private val httpClient: MatrixHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun getDevices(): GetDevicesResponse {
        return httpClient.request {
            method = Get
            url("/_matrix/client/r0/devices")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3devices">matrix spec</a>
     */
    suspend fun getDevice(deviceId: String): Device {
        return httpClient.request {
            method = Get
            url("/_matrix/client/r0/devices/${deviceId}")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun updateDevice(
        deviceId: String,
        displayName: String,
    ) {
        return httpClient.request {
            method = Put
            url("/_matrix/client/r0/devices/${deviceId}")
            body = UpdateDeviceRequest(displayName)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3delete_devices">matrix spec</a>
     */
    suspend fun deleteDevices(
        devices: List<String>,
    ): UIA<Unit> {
        return httpClient.uiaRequest(
            body = DeleteDevicesRequest(devices)
        ) {
            method = Post
            url("/_matrix/client/r0/delete_devices")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#delete_matrixclientv3devicesdeviceid">matrix spec</a>
     */
    suspend fun deleteDevice(
        deviceId: String,
    ): UIA<Unit> {
        return httpClient.uiaRequest {
            method = Delete
            url("/_matrix/client/r0/devices/${deviceId}")
        }
    }
}
