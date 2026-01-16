package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3publicrooms">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/publicRooms")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class GetPublicRooms(
    @SerialName("limit") val limit: Long? = null,
    @SerialName("server") val server: String? = null,
    @SerialName("since") val since: String? = null
) : MatrixEndpoint<Unit, GetPublicRoomsResponse>