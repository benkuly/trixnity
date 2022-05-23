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
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseriddisplayname">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/displayname")
@HttpMethod(GET)
@WithoutAuth
data class GetDisplayName(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, GetDisplayName.Response> {
    @Serializable
    data class Response(
        @SerialName("displayname") val displayName: String?
    )
}