package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint

@Serializable
@Resource("/_matrix/federation/v1/publicRooms")
@HttpMethod(POST)
object GetPublicRoomsWithFilter : MatrixEndpoint<GetPublicRoomsWithFilter.Request, GetPublicRoomsResponse> {
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