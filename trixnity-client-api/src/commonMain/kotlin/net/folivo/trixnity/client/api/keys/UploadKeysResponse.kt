package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

@Serializable
data class UploadKeysResponse(
    @SerialName("one_time_key_counts")
    val oneTimeKeyCounts: Map<KeyAlgorithm, Int>,
)