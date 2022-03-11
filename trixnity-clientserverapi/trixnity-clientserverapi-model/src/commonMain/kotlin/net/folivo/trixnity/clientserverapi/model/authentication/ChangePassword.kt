package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint

@Serializable
@Resource("/_matrix/client/v3/account/password")
object ChangePassword : MatrixJsonEndpoint<ChangePassword.Request, Unit>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("new_password")
        val newPassword: String,
        @SerialName("logout_devices")
        val logoutDevices: Boolean?
    )
}