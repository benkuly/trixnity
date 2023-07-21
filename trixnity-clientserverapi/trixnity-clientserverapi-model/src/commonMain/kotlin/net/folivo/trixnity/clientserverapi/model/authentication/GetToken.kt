package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#post_matrixclientv1loginget_token">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/login/get_token")
@HttpMethod(POST)
data class GetToken(
    @SerialName("user_id") val asUserId: UserId? = null,
) : MatrixUIAEndpoint<Unit, GetToken.Response> {
    @Serializable
    data class Response(
        @SerialName("login_token") val loginToken: String,
        @SerialName("expires_in_ms") val expiresInMs: Long,
    )
}