package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#post_matrixclientv3publicrooms">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/publicRooms")
@HttpMethod(POST)
data class GetPublicRoomsWithFilter(
    @SerialName("server") val server: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<GetPublicRoomsWithFilter.Request, GetPublicRoomsResponse> {
    @Serializable
    data class Request(
        @SerialName("filter") val filter: Filter? = null,
        @SerialName("include_all_networks") val includeAllNetworks: Boolean? = null,
        @SerialName("limit") val limit: Long? = null,
        @SerialName("since") val since: String? = null,
        @SerialName("third_party_instance_id") val thirdPartyInstanceId: String? = null,
    ) {
        @Serializable
        data class Filter(
            @SerialName("generic_search_term") val genericSearchTerm: String? = null
        )
    }
}