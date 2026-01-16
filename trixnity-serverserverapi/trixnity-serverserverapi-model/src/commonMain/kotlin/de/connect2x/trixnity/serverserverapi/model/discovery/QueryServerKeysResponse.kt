package de.connect2x.trixnity.serverserverapi.model.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.keys.Signed

@Serializable
data class QueryServerKeysResponse(
    @SerialName("server_keys")
    val serverKeys: Set<Signed<ServerKeys, String>>,
)