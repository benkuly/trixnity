package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.contentSerializer
import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3roomsroomidsendeventtypetxnid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/send/{type}/{txnId}")
@HttpMethod(PUT)
data class SendMessageEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("txnId") val txnId: String,
    @SerialName("ts") val ts: Long? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @MSC4354 @SerialName("sticky_duration_ms") private val stickyDurationMsStable: Long? = null,
    @MSC4354 @SerialName("org.matrix.msc4354.sticky_duration_ms") private val stickyDurationMsUnstable: Long? = null,
) : MatrixEndpoint<MessageEventContent, SendEventResponse> {
    @MSC4354
    val stickyDurationMs: Long? get() = stickyDurationMsStable ?: stickyDurationMsUnstable
    
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: MessageEventContent?
    ): KSerializer<MessageEventContent> {
        return mappings.message.contentSerializer(type, value)
    }
}
