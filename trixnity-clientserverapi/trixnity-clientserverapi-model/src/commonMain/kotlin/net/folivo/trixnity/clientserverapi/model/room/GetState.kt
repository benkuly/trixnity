package net.folivo.trixnity.clientserverapi.model.room

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
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3roomsroomidstate">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/state")
@HttpMethod(GET)
data class GetState(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<Unit, List<StateEvent<*>>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: List<StateEvent<*>>?
    ): KSerializer<List<StateEvent<*>>>? {
        return ListSerializer(requireNotNull(json.serializersModule.getContextual(StateEvent::class)))
    }
}