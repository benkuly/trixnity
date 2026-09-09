package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import de.connect2x.trixnity.clientserverapi.model.media.GetMediaConfig
import de.connect2x.trixnity.clientserverapi.model.server.GetCapabilities
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
@OptIn(MSC4143::class)
data class ServerData(
    val versions: GetVersions.Response,
    val mediaConfig: GetMediaConfig.Response,
    val capabilities: GetCapabilities.Response?,
    /** Is null when no OAuth2 Auth Provider is used. */
    val auth: ServerMetadata? = null, // TODO remove default value in future, as it is set on a daily basis
    val rtcTransports: List<@Contextual RtcTransport>? = null,
)
