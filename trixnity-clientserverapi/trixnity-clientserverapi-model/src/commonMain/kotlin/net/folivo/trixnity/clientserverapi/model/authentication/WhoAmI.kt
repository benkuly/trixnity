package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3accountwhoami">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/whoami")
@HttpMethod(GET)
data class WhoAmI(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, WhoAmI.Response> {
    @Serializable
    data class Response(
        @SerialName("user_id") val userId: UserId,
        @SerialName("device_id") val deviceId: String?,
        @SerialName("is_guest") val isGuest: Boolean?
    )
}