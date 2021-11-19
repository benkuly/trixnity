package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.Keys

@Serializable
data class ClaimKeysResponse(
    @SerialName("failures")
    val failures: Map<String, JsonElement>,
    @SerialName("one_time_keys")
    val oneTimeKeys: Map<UserId, Map<String, Keys>>
)