package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

@Serializable
data class SetDeviceKeysResponse(
    @SerialName("one_time_key_counts")
    val oneTimeKeyCounts: Map<KeyAlgorithm, Int>,
)