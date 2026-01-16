package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent

/**
 * @see <a href="https://spec.matrix.org/v1.4/client-server-api/#get_matrixclientv1roomsroomidthreads">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/rooms/{roomId}/threads")
@HttpMethod(GET)
data class GetThreads(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("from") val from: String? = null,
    @SerialName("include") val include: Include? = null,
    @SerialName("limit") val limit: Long? = null,
) : MatrixEndpoint<Unit, GetThreads.Response> {

    @Serializable
    data class Response(
        @SerialName("next_batch") val end: String? = null,
        @SerialName("chunk") val chunk: List<@Contextual RoomEvent<*>>,
    )

    @Serializable
    enum class Include {
        @SerialName("all")
        ALL,

        @SerialName("participated")
        PARTICIPATED
    }
}