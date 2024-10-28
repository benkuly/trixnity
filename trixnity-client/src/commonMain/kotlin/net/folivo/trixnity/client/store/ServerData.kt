package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.clientserverapi.model.server.Capabilities
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions

@Serializable
data class ServerData(
    val versions: GetVersions.Response,
    val mediaConfig: GetMediaConfig.Response,
    // TODO remove default value in future, as it is set on a daily basis
    val capabilities: GetCapabilities.Response? = GetCapabilities.Response(Capabilities(setOf())),
)