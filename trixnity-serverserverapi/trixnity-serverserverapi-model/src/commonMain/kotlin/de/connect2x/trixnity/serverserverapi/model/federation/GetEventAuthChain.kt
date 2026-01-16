package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentDataUnit

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1event_authroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/event_auth/{roomId}/{eventId}")
@HttpMethod(GET)
data class GetEventAuthChain(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<Unit, GetEventAuthChain.Response> {
    @Serializable
    data class Response(
        @SerialName("auth_chain") val authChain: List<SignedPersistentDataUnit<*>>
    )
}