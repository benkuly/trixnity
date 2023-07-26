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
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3loginssoredirectidpid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/login/sso/redirect/{idpId}")
@HttpMethod(GET)
@WithoutAuth
data class SSORedirectTo(
    @SerialName("idpId") val idpId: String,
    @SerialName("redirectUrl") val redirectUrl: String,
) : MatrixEndpoint<Unit, Unit> {
    override val responseContentType: ContentType?
        get() = null
}