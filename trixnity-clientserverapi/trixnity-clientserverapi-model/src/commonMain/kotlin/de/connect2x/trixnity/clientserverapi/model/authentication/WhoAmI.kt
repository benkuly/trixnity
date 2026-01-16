package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3accountwhoami">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/whoami")
@HttpMethod(GET)
data object WhoAmI : MatrixEndpoint<Unit, WhoAmI.Response> {
    @Serializable
    data class Response(
        @SerialName("user_id") val userId: UserId,
        @SerialName("device_id") val deviceId: String?,
        @SerialName("is_guest") val isGuest: Boolean?
    )
}