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

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3useruseridroomsroomidtagstag">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/rooms/{roomId}/tags/{tag}")
@HttpMethod(PUT)
data class SetRoomTag(
    @SerialName("userId") val userId: UserId,
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("tag") val tag: String,
) : MatrixEndpoint<TagEventContent.Tag, Unit>