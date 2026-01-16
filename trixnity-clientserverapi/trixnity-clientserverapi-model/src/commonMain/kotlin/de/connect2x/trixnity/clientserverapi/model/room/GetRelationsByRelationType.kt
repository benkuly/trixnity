package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv1roomsroomidrelationseventidreltype">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/rooms/{roomId}/relations/{eventId}/{relType}")
@HttpMethod(GET)
data class GetRelationsByRelationType(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("relType") val relationType: RelationType,
    @SerialName("from") val from: String? = null,
    @SerialName("to") val to: String? = null,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("recurse") val recurse: Boolean? = null,
) : MatrixEndpoint<Unit, GetRelationsResponse>