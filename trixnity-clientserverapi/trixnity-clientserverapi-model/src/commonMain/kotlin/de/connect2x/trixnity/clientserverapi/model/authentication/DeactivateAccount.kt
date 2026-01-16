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
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3accountdeactivate">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/deactivate")
@HttpMethod(POST)
@Auth(AuthRequired.OPTIONAL)
data object DeactivateAccount : MatrixUIAEndpoint<DeactivateAccount.Request, DeactivateAccount.Response> {
    @Serializable
    data class Request(
        @SerialName("id_server")
        val identityServer: String?,
        @SerialName("erase")
        val erase: Boolean? = null,
    )

    @Serializable
    data class Response(
        @SerialName("id_server_unbind_result")
        val idServerUnbindResult: IdServerUnbindResult? = null,
    )
}