package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1publicrooms">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/publicRooms")
@HttpMethod(GET)
data class GetPublicRooms(
    @SerialName("include_all_networks") val includeAllNetworks: Boolean? = null,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("since") val since: String? = null,
    @SerialName("third_party_instance_id") val thirdPartyInstanceId: String? = null
) : MatrixEndpoint<Unit, GetPublicRoomsResponse>