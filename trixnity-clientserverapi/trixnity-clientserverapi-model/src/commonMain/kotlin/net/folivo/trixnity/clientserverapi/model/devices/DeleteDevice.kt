package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/devices/{deviceId}")
data class DeleteDevice(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, Unit>() {
    @Transient
    override val method = Delete
}