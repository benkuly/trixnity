package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchUsersRequest(
    @SerialName("search_term") val searchTerm: String,
    @SerialName("limit") val limit: Int?,
)