package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3logoutall">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/logout/all")
@HttpMethod(POST)
data object LogoutAll : MatrixEndpoint<Unit, Unit> {
    @Transient
    override val requestContentType: ContentType? = null
}