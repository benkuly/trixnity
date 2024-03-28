package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#post_matrixfederationv1publicrooms">matrix spec</a>
 */
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
            @SerialName("generic_search_term") val genericSearchTerm: String? = null,
            @SerialName("room_types") val roomTypes: Set<CreateEventContent.RoomType?>? = null,
        )
    }
}