package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/messages")
data class GetEvents(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("from") val from: String,
    @SerialName("to") val to: String? = null,
    @SerialName("dir") val dir: Direction,
    @SerialName("limit") val limit: Long,
    @SerialName("filter") val filter: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetEvents.Response>() {
    @Transient
    override val method = Get

    @Serializable
    enum class Direction {
        @SerialName("f")
        FORWARD,

        @SerialName("b")
        BACKWARDS
    }

    @Serializable
    data class Response(
        @SerialName("start") val start: String,
        @SerialName("end") val end: String? = null,
        @SerialName("chunk") val chunk: List<@Contextual Event.RoomEvent<*>>? = null,
        @SerialName("state") val state: List<@Contextual Event.StateEvent<*>>? = null
    )
}