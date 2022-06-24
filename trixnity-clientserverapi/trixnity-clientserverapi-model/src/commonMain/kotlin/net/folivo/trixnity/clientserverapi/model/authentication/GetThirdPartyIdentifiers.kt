package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#get_matrixclientv3account3pid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/3pid")
@HttpMethod(GET)
data class GetThirdPartyIdentifiers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetThirdPartyIdentifiers.Response> {
    @Serializable
    data class Response(
        @SerialName("threepids")
        val thirdPartyIdentifiers: Set<ThirdPartyIdentifier>,
    )
}