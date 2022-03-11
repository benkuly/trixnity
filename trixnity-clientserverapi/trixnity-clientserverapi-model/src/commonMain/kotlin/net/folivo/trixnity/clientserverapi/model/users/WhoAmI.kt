package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/account/whoami")
data class WhoAmI(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, WhoAmI.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("user_id") val userId: UserId,
        @SerialName("device_id") val deviceId: String?,
        @SerialName("is_guest") val isGuest: Boolean?
    )
}