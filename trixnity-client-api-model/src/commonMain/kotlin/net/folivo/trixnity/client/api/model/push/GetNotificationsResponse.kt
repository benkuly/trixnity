package net.folivo.trixnity.client.api.model.push

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.push.PushAction

@Serializable
data class GetNotificationsResponse(
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