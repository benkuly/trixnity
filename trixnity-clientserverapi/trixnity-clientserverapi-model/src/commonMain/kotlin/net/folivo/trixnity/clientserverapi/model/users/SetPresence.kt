package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent

@Serializable
@Resource("/_matrix/client/v3/presence/{userId}/status")
@HttpMethod(PUT)
data class SetPresence(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetPresence.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("presence") val presence: PresenceEventContent.Presence,
        @SerialName("status_msg") val statusMessage: String?
    )
}