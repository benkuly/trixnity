package net.folivo.trixnity.serverserverapi.model.transaction

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit

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