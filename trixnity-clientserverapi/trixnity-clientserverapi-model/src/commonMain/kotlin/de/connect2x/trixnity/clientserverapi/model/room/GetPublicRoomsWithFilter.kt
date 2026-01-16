package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3publicrooms">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/publicRooms")
@HttpMethod(POST)
data class GetPublicRoomsWithFilter(
    @SerialName("server") val server: String? = null,
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
            @SerialName("generic_search_term") val genericSearchTerm: String? = null,
            @SerialName("room_types") val roomTypes: Set<CreateEventContent.RoomType?>? = null,
        )
    }
}