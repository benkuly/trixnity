package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetDisplayNameResponse(
    @SerialName("displayname") val displayName: String?
)