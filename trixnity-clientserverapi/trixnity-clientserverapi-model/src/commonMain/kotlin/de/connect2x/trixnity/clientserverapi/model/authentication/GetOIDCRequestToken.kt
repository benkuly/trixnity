package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3useruseridopenidrequest_token">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/openid/request_token")
@HttpMethod(POST)
data class GetOIDCRequestToken(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, GetOIDCRequestToken.Response> {
    @Serializable
    data class Response(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("matrix_server_name") val matrixServerName: String,
        @SerialName("token_type") val tokenType: String = "Bearer"
    )
}