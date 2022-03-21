package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/devices")
@HttpMethod(GET)
data class GetDevices(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetDevices.Response> {
    @Serializable
    data class Response(
        @SerialName("devices") val devices: List<Device>,
    )
}