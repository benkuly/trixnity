package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/displayname")
data class GetDisplayName(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetDisplayName.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("displayname") val displayName: String?
    )
}