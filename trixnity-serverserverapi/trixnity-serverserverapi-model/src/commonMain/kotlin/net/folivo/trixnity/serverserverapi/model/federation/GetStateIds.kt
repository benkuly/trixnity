package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1state_idsroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/state_ids/{roomId}")
@HttpMethod(GET)
data class GetStateIds(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("event_id") val eventId: EventId,
) : MatrixEndpoint<Unit, GetStateIds.Response> {
    @Serializable
    data class Response(
        @SerialName("auth_chain_ids")
        val authChainIds: List<EventId>,
        @SerialName("pdu_ids")
        val pduIds: List<EventId>
    )
}