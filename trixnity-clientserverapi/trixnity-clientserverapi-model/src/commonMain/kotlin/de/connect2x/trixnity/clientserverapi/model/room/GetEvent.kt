package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3roomsroomideventeventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/event/{eventId}")
@HttpMethod(GET)
data class GetEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val evenId: EventId,
) : MatrixEndpoint<Unit, RoomEvent<*>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: RoomEvent<*>?
    ): KSerializer<RoomEvent<*>> {
        return requireNotNull(json.serializersModule.getContextual(RoomEvent::class))
    }
}