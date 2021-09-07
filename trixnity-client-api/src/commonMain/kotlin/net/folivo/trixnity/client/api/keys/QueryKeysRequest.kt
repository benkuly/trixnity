package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId

@Serializable
data class QueryKeysRequest(
    @SerialName("device_keys")
    val deviceKeys: Map<MatrixId.UserId, Set<String>>,
    @SerialName("token")
    val token: String?,
    @SerialName("timeout")
    val timeout: Int?,
)