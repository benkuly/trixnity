package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3publicrooms">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/publicRooms")
@HttpMethod(GET)
data class GetPublicRooms(
    @SerialName("limit") val limit: Long? = null,
    @SerialName("server") val server: String? = null,
    @SerialName("since") val since: String? = null
) : MatrixEndpoint<Unit, GetPublicRoomsResponse>