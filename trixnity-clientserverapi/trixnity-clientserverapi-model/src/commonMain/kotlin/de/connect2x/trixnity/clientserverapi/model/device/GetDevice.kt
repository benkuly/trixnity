package de.connect2x.trixnity.clientserverapi.model.device

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/devices/{deviceId}")
@HttpMethod(GET)
data class GetDevice(
    @SerialName("deviceId") val deviceId: String,
) : MatrixEndpoint<Unit, Device>