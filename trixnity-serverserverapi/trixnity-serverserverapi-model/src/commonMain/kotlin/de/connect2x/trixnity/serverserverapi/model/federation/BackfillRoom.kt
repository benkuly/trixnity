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
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1backfillroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/backfill/{roomId}")
@HttpMethod(GET)
class BackfillRoom(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("v") val startFrom: List<EventId>,
    @SerialName("limit") val limit: Long
) : MatrixEndpoint<Unit, PduTransaction>