package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv1loginget_token">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/login/get_token")
@HttpMethod(POST)
data object GetToken : MatrixUIAEndpoint<Unit, GetToken.Response> {
    @Serializable
    data class Response(
        @SerialName("login_token") val loginToken: String,
        @SerialName("expires_in_ms") val expiresInMs: Long,
    )
}