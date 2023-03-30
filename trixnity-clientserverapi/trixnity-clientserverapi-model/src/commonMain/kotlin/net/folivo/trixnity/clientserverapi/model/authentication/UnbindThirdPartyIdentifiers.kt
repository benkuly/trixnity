package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#post_matrixclientv3account3pidunbind">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/unbind")
@HttpMethod(POST)
data class UnbindThirdPartyIdentifiers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<UnbindThirdPartyIdentifiers.Request, UnbindThirdPartyIdentifiers.Response> {
    @Serializable
    data class Request(
        @SerialName("address") val address: String,
        @SerialName("id_server") val idServer: String? = null,
        @SerialName("medium") val medium: ThirdPartyIdentifier.Medium,
    )

    @Serializable
    data class Response(
        @SerialName("id_server_unbind_result")
        val idServerUnbindResult: IdServerUnbindResult,
    )
}