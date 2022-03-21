package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/avatar_url")
@HttpMethod(GET)
@WithoutAuth
data class GetAvatarUrl(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetAvatarUrl.Response> {
    @Serializable
    data class Response(
        @SerialName("avatar_url") val avatarUrl: String?
    )
}