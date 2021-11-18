package net.folivo.trixnity.client.api.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GetAvatarUrlResponse(
    @SerialName("avatar_url") val avatarUrl: String?
)
