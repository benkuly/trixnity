package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3accountpassword">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/password")
@HttpMethod(POST)
@Auth(AuthRequired.OPTIONAL)
object ChangePassword : MatrixUIAEndpoint<ChangePassword.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("new_password")
        val newPassword: String,
        @SerialName("logout_devices")
        val logoutDevices: Boolean? = null
    )
}