package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/event/{eventId}")
data class GetEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val evenId: EventId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, Event<*>>() {
    @Transient
    override val method = Get
}