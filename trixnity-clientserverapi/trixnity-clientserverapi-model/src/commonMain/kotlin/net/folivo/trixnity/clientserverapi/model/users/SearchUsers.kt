package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/user_directory/search")
data class SearchUsers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SearchUsers.Request, SearchUsers.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("search_term") val searchTerm: String,
        @SerialName("limit") val limit: Int?,
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