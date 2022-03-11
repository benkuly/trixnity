package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.push.PushAction

@Serializable
@Resource("/_matrix/client/v3/notifications")
data class GetNotifications(
    @SerialName("from") val from: String? = null,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("only") val only: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetNotifications.Response>() {
    @Transient
    override val method = Get

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
            val event: @Contextual Event<*>,
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