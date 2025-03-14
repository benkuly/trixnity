package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.core.model.UserId

interface DeviceApiClient {
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

class DeviceApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : DeviceApiClient {

    override suspend fun getDevices(asUserId: UserId?): Result<List<Device>> =
        baseClient.request(GetDevices(asUserId)).map { it.devices }

    override suspend fun getDevice(deviceId: String, asUserId: UserId?): Result<Device> =
        baseClient.request(GetDevice(deviceId, asUserId))

    override suspend fun updateDevice(
        deviceId: String,
        displayName: String,
        asUserId: UserId?
    ): Result<Unit> =
        baseClient.request(UpdateDevice(deviceId, asUserId), UpdateDevice.Request(displayName))

    override suspend fun deleteDevices(devices: List<String>, asUserId: UserId?): Result<UIA<Unit>> =
        baseClient.uiaRequest(DeleteDevices(asUserId), DeleteDevices.Request(devices))

    override suspend fun deleteDevice(deviceId: String, asUserId: UserId?): Result<UIA<Unit>> =
        baseClient.uiaRequest(DeleteDevice(deviceId, asUserId))
}
