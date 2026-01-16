package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

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