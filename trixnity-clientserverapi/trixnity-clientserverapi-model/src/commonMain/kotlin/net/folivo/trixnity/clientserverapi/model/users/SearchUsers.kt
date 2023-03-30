package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 *  @see <a href="https://spec.matrix.org/v1.6/client-server-api/#post_matrixclientv3user_directorysearch">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user_directory/search")
@HttpMethod(POST)
data class SearchUsers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SearchUsers.Request, SearchUsers.Response> {
    @Serializable
    data class Request(
        @SerialName("search_term") val searchTerm: String,
        @SerialName("limit") val limit: Long?,
    )

    @Serializable
    data class Response(
        @SerialName("limited") val limited: Boolean,
        @SerialName("results") val results: List<SearchUser>,
    ) {
        @Serializable
        data class SearchUser(
            @SerialName("avatar_url") val avatarUrl: String? = null,
            @SerialName("display_name") val displayName: String? = null,
            @SerialName("user_id") val userId: UserId,
        )
    }
}