package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.push.PushAction

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3notifications">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/notifications")
@HttpMethod(GET)
data class GetNotifications(
    @SerialName("from") val from: String? = null,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("only") val only: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetNotifications.Response> {
    @Serializable
    data class Response(
        @SerialName("next_token") val nextToken: String? = null,
        @SerialName("notifications") val notifications: List<Notification>,
    ) {
        @Serializable
        data class Notification(
            @SerialName("actions")
            val actions: Set<PushAction>,
            @SerialName("event")
            val event: @Contextual RoomEvent<*>,
            @SerialName("profile_tag")
            val profileTag: String? = null,
            @SerialName("read")
            val read: Boolean,
            @SerialName("room_id")
            val roomId: RoomId,
            @SerialName("ts")
            val timestamp: Long,
        )
    }
}