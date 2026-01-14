package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.devices.*
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

interface DeviceApiClient {
    /**
     * @see [GetDevices]
     */
    suspend fun getDevices(): Result<List<Device>>

    /**
     * @see [GetDevice]
     */
    suspend fun getDevice(deviceId: String): Result<Device>

    /**
     * @see [UpdateDevice]
     */
    suspend fun updateDevice(
        deviceId: String,
        displayName: String,
    ): Result<Unit>

    /**
     * @see [DeleteDevices]
     */
    suspend fun deleteDevices(devices: List<String>): Result<UIA<Unit>>

    /**
     * @see [DeleteDevice]
     */
    suspend fun deleteDevice(deviceId: String): Result<UIA<Unit>>

    /**
     * @see [GetDehydratedDevice]
     */
    @MSC3814
    suspend fun getDehydratedDevice(): Result<GetDehydratedDevice.Response>

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
    ): Result<SetDehydratedDevice.Response>

    /**
     * @see [DeleteDehydratedDevice]
     */
    @MSC3814
    suspend fun deleteDehydratedDevice(): Result<DeleteDehydratedDevice.Response>

    /**
     * @see [GetDehydratedDeviceEvents]
     */
    @MSC3814
    suspend fun getDehydratedDeviceEvents(
        deviceId: String,
        nextBatch: String? = null,
    ): Result<GetDehydratedDeviceEvents.Response>
}

class DeviceApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : DeviceApiClient {

    override suspend fun getDevices(): Result<List<Device>> =
        baseClient.request(GetDevices).map { it.devices }

    override suspend fun getDevice(deviceId: String): Result<Device> =
        baseClient.request(GetDevice(deviceId))

    override suspend fun updateDevice(
        deviceId: String,
        displayName: String,
    ): Result<Unit> =
        baseClient.request(UpdateDevice(deviceId), UpdateDevice.Request(displayName))

    override suspend fun deleteDevices(devices: List<String>): Result<UIA<Unit>> =
        baseClient.uiaRequest(DeleteDevices, DeleteDevices.Request(devices))

    override suspend fun deleteDevice(deviceId: String): Result<UIA<Unit>> =
        baseClient.uiaRequest(DeleteDevice(deviceId))

    @MSC3814
    override suspend fun getDehydratedDevice(): Result<GetDehydratedDevice.Response> =
        baseClient.request(GetDehydratedDevice)

    @MSC3814
    override suspend fun setDehydratedDevice(
        deviceId: String,
        deviceData: DehydratedDeviceData,
        deviceKeys: SignedDeviceKeys,
        oneTimeKeys: Keys?,
        fallbackKeys: Keys?,
        initialDeviceDisplayName: String?,
    ): Result<SetDehydratedDevice.Response> =
        baseClient.request(
            SetDehydratedDevice,
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
    override suspend fun deleteDehydratedDevice(): Result<DeleteDehydratedDevice.Response> =
        baseClient.request(DeleteDehydratedDevice)

    @MSC3814
    override suspend fun getDehydratedDeviceEvents(
        deviceId: String,
        nextBatch: String?,
    ): Result<GetDehydratedDeviceEvents.Response> =
        baseClient.request(GetDehydratedDeviceEvents(deviceId), GetDehydratedDeviceEvents.Request(nextBatch))
}
