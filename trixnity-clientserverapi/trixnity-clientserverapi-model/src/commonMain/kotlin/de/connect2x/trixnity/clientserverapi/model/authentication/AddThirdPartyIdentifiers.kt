package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3account3pidadd">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid/add")
@HttpMethod(POST)
data object AddThirdPartyIdentifiers : MatrixUIAEndpoint<AddThirdPartyIdentifiers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("sid") val sessionId: String,
    )
}