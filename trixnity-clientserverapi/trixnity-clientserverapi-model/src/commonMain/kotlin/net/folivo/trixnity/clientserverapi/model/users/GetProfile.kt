package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3profileuserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}")
@HttpMethod(GET)
@WithoutAuth
data class GetProfile(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, GetProfile.Response> {
    @Serializable
    data class Response(
        @SerialName("displayname") val displayName: String?,
        @SerialName("avatar_url") val avatarUrl: String?,
    )
}