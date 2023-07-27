package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.TagEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3useruseridroomsroomidtags">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/rooms/{roomId}/tags")
@HttpMethod(GET)
data class GetRoomTags(
    @SerialName("userId") val userId: UserId,
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, TagEventContent>