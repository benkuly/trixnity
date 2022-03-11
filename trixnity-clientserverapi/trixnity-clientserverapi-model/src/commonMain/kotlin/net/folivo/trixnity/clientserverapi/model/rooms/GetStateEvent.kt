package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/state/{type}/{stateKey}")
data class GetStateEvent<C : StateEventContent>(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("stateKey") val stateKey: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, C>() {
    @Transient
    override val method = Get
}