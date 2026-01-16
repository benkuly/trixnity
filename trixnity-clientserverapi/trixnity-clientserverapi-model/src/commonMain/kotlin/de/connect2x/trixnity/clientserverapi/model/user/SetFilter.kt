package de.connect2x.trixnity.clientserverapi.model.user

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3useruseridfilter">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/filter")
@HttpMethod(POST)
data class SetFilter(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<Filters, SetFilter.Response> {
    @Serializable
    data class Response(
        @SerialName("filter_id") val filterId: String
    )
}