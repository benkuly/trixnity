package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1querydirectory">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/query/directory")
@HttpMethod(GET)
data class QueryDirectory(
    @SerialName("room_alias") val roomAlias: RoomAliasId,
) : MatrixEndpoint<Unit, QueryDirectory.Response> {
    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("servers") val servers: Set<String>,
    )
}