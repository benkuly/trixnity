package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#post_matrixclientv3delete_devices">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/delete_devices")
@HttpMethod(POST)
data class DeleteDevices(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixUIAEndpoint<DeleteDevices.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("devices") val devices: List<String>,
    )
}