package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.TagEventContent

@Serializable
@Resource("/_matrix/client/v3/user/{userId}/rooms/{roomId}/tags/{tag}")
@HttpMethod(PUT)
data class SetRoomTag(
    @SerialName("userId") val userId: UserId,
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("tag") val tag: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<TagEventContent.Tag, Unit>