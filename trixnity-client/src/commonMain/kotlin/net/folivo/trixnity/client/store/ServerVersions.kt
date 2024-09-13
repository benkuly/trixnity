package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable

@Serializable
data class ServerVersions(
    val versions: List<String>,
    val unstableFeatures: Map<String, Boolean>,
)