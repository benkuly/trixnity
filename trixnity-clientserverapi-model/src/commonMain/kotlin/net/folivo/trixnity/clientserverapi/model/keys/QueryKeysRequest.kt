package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class QueryKeysRequest(
    @SerialName("device_keys")
    val deviceKeys: Map<UserId, Set<String>>,
    @SerialName("token")
    val token: String?,
    @SerialName("timeout")
    val timeout: Int?,
)