package net.folivo.trixnity.client.api.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SetFilterResponse(
    @SerialName("filter_id") val filterId: String
)