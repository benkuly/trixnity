package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1openiduserinfo">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/openid/userinfo")
@HttpMethod(GET)
data class GetOIDCUserInfo(
    @SerialName("access_token") val accessToken: String,
) : MatrixEndpoint<Unit, GetOIDCUserInfo.Response> {
    @Serializable
    data class Response(
        @SerialName("sub") val sub: UserId,
    )
}