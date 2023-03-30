package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#post_matrixclientv3refresh">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/refresh")
@HttpMethod(POST)
@WithoutAuth
object Refresh : MatrixEndpoint<Refresh.Request, Refresh.Response> {
    @Serializable
    data class Request(
        @SerialName("refresh_token")
        val refreshToken: String? = null,
    )

    @Serializable
    data class Response(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("expires_in_ms")
        val accessTokenExpiresInMs: Long? = null,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
    )
}