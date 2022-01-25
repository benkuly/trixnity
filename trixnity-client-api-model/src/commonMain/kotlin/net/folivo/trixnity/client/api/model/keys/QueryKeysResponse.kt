package net.folivo.trixnity.client.api.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
data class QueryKeysResponse(
    @SerialName("failures")
    val failures: Map<UserId, JsonElement>,
    @SerialName("device_keys")
    val deviceKeys: Map<UserId, Map<String, Signed<DeviceKeys, UserId>>>?,
    @SerialName("master_keys")
    val masterKeys: Map<UserId, Signed<CrossSigningKeys, UserId>>?,
    @SerialName("self_signing_keys")
    val selfSigningKeys: Map<UserId, Signed<CrossSigningKeys, UserId>>?,
    @SerialName("user_signing_keys")
    val userSigningKeys: Map<UserId, Signed<CrossSigningKeys, UserId>>?,
)