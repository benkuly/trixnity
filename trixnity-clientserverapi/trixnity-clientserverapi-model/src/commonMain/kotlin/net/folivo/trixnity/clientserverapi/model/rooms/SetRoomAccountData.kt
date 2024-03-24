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
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/rooms/{roomId}/account_data/{type}")
@HttpMethod(PUT)
data class SetRoomAccountData(
    @SerialName("userId") val userId: UserId,
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<RoomAccountDataEventContent, Unit> {
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: RoomAccountDataEventContent?
    ): KSerializer<RoomAccountDataEventContent> =
        mappings.roomAccountData.contentSerializer(type, value)
}