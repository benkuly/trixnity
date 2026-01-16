package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3registeravailable">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/register/available")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class IsUsernameAvailable(
    @SerialName("username") val username: String,
) : MatrixEndpoint<Unit, IsUsernameAvailable.Response> {
    @Serializable
    data class Response(@SerialName("available") val available: Boolean)
}