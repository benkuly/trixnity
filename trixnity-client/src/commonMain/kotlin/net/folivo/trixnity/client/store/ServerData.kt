package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.clientserverapi.model.server.GetVersions

@Serializable
data class ServerData(
    val versions: GetVersions.Response,
    val mediaConfig: GetMediaConfig.Response,
)