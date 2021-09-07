package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Signed

@Serializable
data class QueryKeysResponse(
    @SerialName("failures")
    val failures: Map<UserId, JsonElement>,
    @SerialName("device_keys")
    val deviceKeys: Map<UserId, Map<String, Signed<DeviceKeys, UserId>>>
)