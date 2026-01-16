package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1make_joinroomiduserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/make_join/{roomId}/{userId}")
@HttpMethod(GET)
data class MakeJoin(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("userId") val userId: UserId,
    @SerialName("ver") val supportedRoomVersions: Set<String>? = null,
) : MatrixEndpoint<Unit, MakeJoin.Response> {
    @Serializable
    data class Response(
        @SerialName("event")
        val eventTemplate: @Contextual PersistentStateDataUnit<MemberEventContent>? = null,
        @SerialName("room_version")
        val roomVersion: String? = null,
    )
}