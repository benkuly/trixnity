package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

@Serializable
@Resource("/_matrix/client/v3/user/{userId}/rooms/{roomId}/account_data/{type}")
data class GetRoomAccountData<C : RoomAccountDataEventContent>(
    @SerialName("userId") val userId: UserId,
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, C>() {
    @Transient
    override val method = Get
}