package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidstate">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/state")
@HttpMethod(GET)
data class GetState(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, List<ClientEvent.StateEvent<*>>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json
    ): KSerializer<List<ClientEvent.StateEvent<*>>> {
        return ListSerializer(requireNotNull(json.serializersModule.getContextual(ClientEvent.StateEvent::class)))
    }
}