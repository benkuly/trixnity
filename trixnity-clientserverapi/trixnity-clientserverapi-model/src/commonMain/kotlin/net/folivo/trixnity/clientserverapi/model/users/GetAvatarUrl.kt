package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#get_matrixclientv3profileuseridavatar_url">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/avatar_url")
@HttpMethod(GET)
data class GetAvatarUrl(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, GetAvatarUrl.Response> {
    @Serializable
    data class Response(
        @SerialName("avatar_url") val avatarUrl: String?
    )
}