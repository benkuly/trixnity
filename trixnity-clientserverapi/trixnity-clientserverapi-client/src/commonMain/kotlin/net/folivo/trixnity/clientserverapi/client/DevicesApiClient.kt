package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.core.model.UserId

interface DevicesApiClient {
    /**
     * @see [GetDevices]
     */
    suspend fun getDevices(asUserId: UserId? = null): Result<List<Device>>

    /**
     * @see [GetDevice]
     */
    suspend fun getDevice(deviceId: String, asUserId: UserId? = null): Result<Device>

    /**
     * @see [UpdateDevice]
     */
    suspend fun updateDevice(
        deviceId: String,
        displayName: String,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see [DeleteDevices]
     */
    suspend fun deleteDevices(devices: List<String>, asUserId: UserId? = null): Result<UIA<Unit>>

    /**
     * @see [DeleteDevice]
     */
    suspend fun deleteDevice(deviceId: String, asUserId: UserId? = null): Result<UIA<Unit>>
}

class DevicesApiClientImpl(
    private val httpClient: MatrixClientServerApiHttpClient
) : DevicesApiClient {

    override suspend fun getDevices(asUserId: UserId?): Result<List<Device>> =
        httpClient.request(GetDevices(asUserId)).map { it.devices }

    override suspend fun getDevice(deviceId: String, asUserId: UserId?): Result<Device> =
        httpClient.request(GetDevice(deviceId.e(), asUserId))

    override suspend fun updateDevice(
        deviceId: String,
        displayName: String,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(UpdateDevice(deviceId.e(), asUserId), UpdateDevice.Request(displayName))

    override suspend fun deleteDevices(devices: List<String>, asUserId: UserId?): Result<UIA<Unit>> =
        httpClient.uiaRequest(DeleteDevices(asUserId), DeleteDevices.Request(devices))

    override suspend fun deleteDevice(deviceId: String, asUserId: UserId?): Result<UIA<Unit>> =
        httpClient.uiaRequest(DeleteDevice(deviceId.e(), asUserId))
}
