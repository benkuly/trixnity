package net.folivo.trixnity.client.api.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class SearchUsersResponse(
    @SerialName("limited") val limited: Boolean,
    @SerialName("results") val results: List<SearchUser>,
)

@Serializable
data class SearchUser(
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("user_id") val userId: UserId,
)
