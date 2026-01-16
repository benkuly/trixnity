package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#put_matrixfederationv2inviteroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v2/invite/{roomId}/{eventId}")
@HttpMethod(PUT)
data class Invite(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<Invite.Request, Invite.Response> {
    @Serializable
    data class Request(
        @SerialName("event")
        val event: Signed<@Contextual PersistentStateDataUnit<MemberEventContent>, String>,
        @SerialName("invite_room_state")
        val inviteRoomState: List<@Contextual StrippedStateEvent<*>>,
        @SerialName("room_version")
        val roomVersion: String,
    )

    @Serializable
    data class Response(
        @SerialName("event")
        val event: Signed<@Contextual PersistentStateDataUnit<MemberEventContent>, String>,
    )
}