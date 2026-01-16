package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import de.connect2x.trixnity.clientserverapi.model.media.GetMediaConfig
import de.connect2x.trixnity.clientserverapi.model.server.GetCapabilities
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions

@Serializable
data class ServerData(
    val versions: GetVersions.Response,
    val mediaConfig: GetMediaConfig.Response,
    val capabilities: GetCapabilities.Response?,
    /**
     * Is null when no OAuth2 Auth Provider is used.
     */
    val auth: ServerMetadata? = null, // TODO remove default value in future, as it is set on a daily basis
)