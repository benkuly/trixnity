package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetProfileResponse(
    @SerialName("displayname") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
)
