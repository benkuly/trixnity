package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventContentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#put_matrixclientv3roomsroomidsendeventtypetxnid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/send/{type}/{txnId}")
@HttpMethod(PUT)
data class SendMessageEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("txnId") val txnId: String,
    @SerialName("user_id") val asUserId: UserId? = null,
    @SerialName("ts") val ts: Long? = null,
) : MatrixEndpoint<MessageEventContent, SendEventResponse> {
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json
    ): KSerializer<MessageEventContent> {
        return MessageEventContentSerializer(mappings.message, type)
    }
}