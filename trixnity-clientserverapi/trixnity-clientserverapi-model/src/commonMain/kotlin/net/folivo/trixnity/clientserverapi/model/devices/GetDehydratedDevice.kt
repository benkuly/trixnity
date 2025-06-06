package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device")
@HttpMethod(GET)
@MSC3814
data class GetDehydratedDevice(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetDehydratedDevice.Response> {
    @Serializable
    data class Response(
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("device_data")
        val deviceData: DehydratedDeviceData,
    )
}