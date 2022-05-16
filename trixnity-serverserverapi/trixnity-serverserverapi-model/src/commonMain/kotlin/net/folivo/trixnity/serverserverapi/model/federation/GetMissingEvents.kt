package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

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