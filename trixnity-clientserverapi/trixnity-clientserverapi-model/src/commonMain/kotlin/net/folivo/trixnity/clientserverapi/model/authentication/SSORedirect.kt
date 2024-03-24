package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3loginssoredirect">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/login/sso/redirect")
@HttpMethod(GET)
@WithoutAuth
data class SSORedirect(
    @SerialName("redirectUrl") val redirectUrl: String,
) : MatrixEndpoint<Unit, Unit> {
    override val responseContentType: ContentType?
        get() = null
}