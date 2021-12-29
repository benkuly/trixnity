package net.folivo.trixnity.client.api.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetDisplayNameRequest (
    @SerialName("displayname") val displayName: String?
)