package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/dehydrated_device")
@HttpMethod(PUT)
@MSC3814
data class SetDehydratedDevice(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetDehydratedDevice.Request, SetDehydratedDevice.Response> {
    @Serializable
    data class Request(
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("device_data")
        val deviceData: DehydratedDeviceData,
        @SerialName("device_keys")
        val deviceKeys: SignedDeviceKeys,
        @SerialName("one_time_keys")
        val oneTimeKeys: Keys? = null,
        @SerialName("fallback_keys")
        val fallbackKeys: Keys? = null,
        @SerialName("initial_device_display_name")
        val initialDeviceDisplayName: String? = null,
    )

    @Serializable
    data class Response(
        @SerialName("device_id")
        val deviceId: String,
    )
}