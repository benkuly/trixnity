package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/displayname")
data class SetDisplayName(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetDisplayName.Request, Unit>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request(
        @SerialName("displayname") val displayName: String?
    )
}