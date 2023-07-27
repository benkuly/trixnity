package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#post_matrixclientv3account3pidbind">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/bind")
@HttpMethod(POST)
data class BindThirdPartyIdentifiers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<BindThirdPartyIdentifiers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("sid") val sessionId: String,
        @SerialName("id_access_token") val idAccessToken: String,
        @SerialName("id_server") val idServer: String,
    )
}