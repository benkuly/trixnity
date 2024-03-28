package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1make_leaveroomiduserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/make_leave/{roomId}/{userId}")
@HttpMethod(GET)
data class MakeLeave(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Unit, MakeLeave.Response> {
    @Serializable
    data class Response(
        @SerialName("event")
        val eventTemplate: @Contextual PersistentStateDataUnit<MemberEventContent>? = null,
        @SerialName("room_version")
        val roomVersion: String? = null,
    )
}