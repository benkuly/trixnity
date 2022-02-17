package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetFilterResponse(
    @SerialName("filter_id") val filterId: String
)