package de.connect2x.trixnity.clientserverapi.model.device

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")
@HttpMethod(GET)
@MSC3814
data object GetDehydratedDevice : MatrixEndpoint<Unit, GetDehydratedDevice.Response> {
    @Serializable
    data class Response(
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("device_data")
        val deviceData: DehydratedDeviceData,
    )
}