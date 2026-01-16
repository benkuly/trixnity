package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.contentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/state/{type}/{stateKey?}")
@HttpMethod(PUT)
data class SendStateEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("stateKey") val stateKey: String = "",
    @SerialName("ts") val ts: Long? = null,
) : MatrixEndpoint<StateEventContent, SendEventResponse> {
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: StateEventContent?
    ): KSerializer<StateEventContent> =
        mappings.state.contentSerializer(type, value)
}