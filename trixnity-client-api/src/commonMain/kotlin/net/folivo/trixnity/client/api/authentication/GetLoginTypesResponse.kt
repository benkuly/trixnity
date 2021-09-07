package net.folivo.trixnity.client.api.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetLoginTypesResponse(
    @SerialName("flows")
    val flows: Set<LoginType>,
)