package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

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

    /**
     * @see [GetDehydratedDevice]
     */
    @MSC3814
    suspend fun getDehydratedDevice(asUserId: UserId? = null): Result<GetDehydratedDevice.Response>

    /**
     * @see [SetDehydratedDevice]
     */
    @MSC3814
    suspend fun setDehydratedDevice(
        deviceId: String,
        deviceData: DehydratedDeviceData,
        deviceKeys: SignedDeviceKeys,
        oneTimeKeys: Keys? = null,
        fallbackKeys: Keys? = null,
        initialDeviceDisplayName: String? = null,
        asUserId: UserId? = null
    ): Result<SetDehydratedDevice.Response>

    /**
     * @see [DeleteDehydratedDevice]
     */
    @MSC3814
    suspend fun deleteDehydratedDevice(asUserId: UserId? = null): Result<DeleteDehydratedDevice.Response>

    /**
     * @see [GetDehydratedDeviceEvents]
     */
    @MSC3814
    suspend fun getDehydratedDeviceEvents(
        deviceId: String,
        nextBatch: String? = null,
        asUserId: UserId? = null
    ): Result<GetDehydratedDeviceEvents.Response>
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

    @MSC3814
    override suspend fun getDehydratedDevice(asUserId: UserId?): Result<GetDehydratedDevice.Response> =
        baseClient.request(GetDehydratedDevice(asUserId))

    @MSC3814
    override suspend fun setDehydratedDevice(
        deviceId: String,
        deviceData: DehydratedDeviceData,
        deviceKeys: SignedDeviceKeys,
        oneTimeKeys: Keys?,
        fallbackKeys: Keys?,
        initialDeviceDisplayName: String?,
        asUserId: UserId?
    ): Result<SetDehydratedDevice.Response> =
        baseClient.request(
            SetDehydratedDevice(asUserId),
            SetDehydratedDevice.Request(
                deviceId = deviceId,
                deviceData = deviceData,
                deviceKeys = deviceKeys,
                oneTimeKeys = oneTimeKeys,
                fallbackKeys = fallbackKeys,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
        )

    @MSC3814
    override suspend fun deleteDehydratedDevice(asUserId: UserId?): Result<DeleteDehydratedDevice.Response> =
        baseClient.request(DeleteDehydratedDevice(asUserId))

    @MSC3814
    override suspend fun getDehydratedDeviceEvents(
        deviceId: String,
        nextBatch: String?,
        asUserId: UserId?
    ): Result<GetDehydratedDeviceEvents.Response> =
        baseClient.request(GetDehydratedDeviceEvents(deviceId, asUserId), GetDehydratedDeviceEvents.Request(nextBatch))
}
