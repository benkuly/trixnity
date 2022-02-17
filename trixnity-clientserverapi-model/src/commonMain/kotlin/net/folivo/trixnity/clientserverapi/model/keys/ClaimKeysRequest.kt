package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

@Serializable
data class ClaimKeysRequest(
    @SerialName("one_time_keys")
    val oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
    @SerialName("timeout")
    val timeout: Int?,
)