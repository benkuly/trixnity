package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

@Serializable
@Resource("/_matrix/federation/v1/backfill/{roomId}")
@HttpMethod(HttpMethodType.GET)
class BackfillRoom(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("v") val startFrom: List<EventId>,
    @SerialName("limit") val limit: Int
) : MatrixEndpoint<Unit, PduTransaction>