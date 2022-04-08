package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/user/{userId}/filter")
@HttpMethod(POST)
data class SetFilter(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Filters, SetFilter.Response> {
    @Serializable
    data class Response(
        @SerialName("filter_id") val filterId: String
    )
}