package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#post_matrixfederationv1get_missing_eventsroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/get_missing_events/{roomId}")
@HttpMethod(POST)
class GetMissingEvents(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<GetMissingEvents.Request, PduTransaction> {
    @Serializable
    data class Request(
        @SerialName("earliest_events") val earliestEvents: List<EventId>,
        @SerialName("latest_events") val latestEvents: List<EventId>,
        @SerialName("limit") val limit: Long? = null,
        @SerialName("min_depth") val minDepth: Long? = null,
    )
}