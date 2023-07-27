package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3roomsroomideventeventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/event/{eventId}")
@HttpMethod(GET)
data class GetEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val evenId: EventId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, Event<*>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json
    ): KSerializer<Event<*>> {
        return requireNotNull(json.serializersModule.getContextual(Event::class))
    }
}