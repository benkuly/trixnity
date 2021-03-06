package net.folivo.trixnity.client.rest.api.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionsResponse(
    @SerialName("versions") val versions: List<String>,
    @SerialName("unstable_features") val unstable_features: Map<String, Boolean>
)