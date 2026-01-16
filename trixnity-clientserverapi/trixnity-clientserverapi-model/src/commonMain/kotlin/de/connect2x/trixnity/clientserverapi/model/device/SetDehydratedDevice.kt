package de.connect2x.trixnity.clientserverapi.model.device

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")
@HttpMethod(PUT)
@MSC3814
data object SetDehydratedDevice : MatrixEndpoint<SetDehydratedDevice.Request, SetDehydratedDevice.Response> {
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