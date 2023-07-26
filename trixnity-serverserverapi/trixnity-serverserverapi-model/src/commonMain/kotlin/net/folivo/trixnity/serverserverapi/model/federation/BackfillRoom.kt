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
 * @see <a href="https://spec.matrix.org/v1.7/server-server-api/#get_matrixfederationv1backfillroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/backfill/{roomId}")
@HttpMethod(GET)
class BackfillRoom(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("v") val startFrom: List<EventId>,
    @SerialName("limit") val limit: Long
) : MatrixEndpoint<Unit, PduTransaction>