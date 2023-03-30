package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#post_matrixclientv3account3pidadd">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/add")
@HttpMethod(POST)
data class AddThirdPartyIdentifiers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixUIAEndpoint<AddThirdPartyIdentifiers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("sid") val sessionId: String,
    )
}