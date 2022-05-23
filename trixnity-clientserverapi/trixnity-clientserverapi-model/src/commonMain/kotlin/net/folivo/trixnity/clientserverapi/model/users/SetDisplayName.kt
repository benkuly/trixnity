package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseriddisplayname">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/displayname")
@HttpMethod(PUT)
data class SetDisplayName(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetDisplayName.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("displayname") val displayName: String?
    )
}