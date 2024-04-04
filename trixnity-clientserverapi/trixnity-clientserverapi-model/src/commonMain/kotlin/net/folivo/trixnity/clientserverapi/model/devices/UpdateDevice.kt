package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/devices/{deviceId}")
@HttpMethod(PUT)
data class UpdateDevice(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<UpdateDevice.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("display_name") val displayName: String
    )
}