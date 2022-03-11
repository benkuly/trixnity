package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/delete_devices")
data class DeleteDevices(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<DeleteDevices.Request, Unit>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("devices") val devices: List<String>,
    )
}