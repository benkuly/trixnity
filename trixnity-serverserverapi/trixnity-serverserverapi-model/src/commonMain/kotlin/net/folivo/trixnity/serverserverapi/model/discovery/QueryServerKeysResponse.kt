package net.folivo.trixnity.serverserverapi.model.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
data class QueryServerKeysResponse(
    @SerialName("server_keys")
    val serverKeys: Set<Signed<ServerKeys, String>>,
)