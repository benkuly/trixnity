package net.folivo.trixnity.client.api.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetAvatarUrlRequest (
    @SerialName("avatar_url") val avatarUrl: String
)