package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1openiduserinfo">matrix spec</a>
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