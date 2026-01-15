package net.folivo.trixnity.clientserverapi.model.device

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3delete_devices">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/delete_devices")
@HttpMethod(POST)
data object DeleteDevices : MatrixUIAEndpoint<DeleteDevices.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("devices") val devices: List<String>,
    )
}