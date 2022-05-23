package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountpassword">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/password")
@HttpMethod(POST)
object ChangePassword : MatrixUIAEndpoint<ChangePassword.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("new_password")
        val newPassword: String,
        @SerialName("logout_devices")
        val logoutDevices: Boolean? = null
    )
}