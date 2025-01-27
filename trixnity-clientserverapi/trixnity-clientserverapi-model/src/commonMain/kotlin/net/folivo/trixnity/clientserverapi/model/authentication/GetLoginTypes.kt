package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3login">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/login")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetLoginTypes : MatrixEndpoint<Unit, GetLoginTypes.Response> {
    @Serializable
    data class Response(
        @SerialName("flows")
        val flows: Set<LoginType>,
    )
}