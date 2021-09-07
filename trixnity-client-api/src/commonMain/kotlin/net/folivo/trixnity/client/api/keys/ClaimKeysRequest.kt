package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

@Serializable
data class ClaimKeysRequest(
    @SerialName("one_time_keys")
    val oneTimeKeys: Map<MatrixId.UserId, Map<String, KeyAlgorithm>>,
    @SerialName("timeout")
    val timeout: Int?,
)