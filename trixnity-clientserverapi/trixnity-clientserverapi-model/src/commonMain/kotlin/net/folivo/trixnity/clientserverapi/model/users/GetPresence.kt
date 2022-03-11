package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent

@Serializable
@Resource("/_matrix/client/v3/presence/{userId}/status")
data class GetPresence(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, PresenceEventContent>() {
    @Transient
    override val method = Get
}