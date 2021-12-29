package net.folivo.trixnity.client.api.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetFilterResponse(
    @SerialName("filter_id") val filterId: String
)