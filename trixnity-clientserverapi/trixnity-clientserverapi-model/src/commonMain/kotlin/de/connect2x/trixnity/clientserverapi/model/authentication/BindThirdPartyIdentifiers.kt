package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3account3pidbind">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/bind")
@HttpMethod(POST)
data object BindThirdPartyIdentifiers : MatrixEndpoint<BindThirdPartyIdentifiers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("sid") val sessionId: String,
        @SerialName("id_access_token") val idAccessToken: String,
        @SerialName("id_server") val idServer: String,
    )
}