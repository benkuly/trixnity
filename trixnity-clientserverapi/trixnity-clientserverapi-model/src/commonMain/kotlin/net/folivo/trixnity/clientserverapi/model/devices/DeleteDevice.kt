package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/devices/{deviceId}")
@HttpMethod(DELETE)
data class DeleteDevice(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixUIAEndpoint<Unit, Unit>