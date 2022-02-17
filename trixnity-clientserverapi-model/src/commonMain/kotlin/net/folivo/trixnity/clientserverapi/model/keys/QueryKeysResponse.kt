package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
data class QueryKeysResponse(
    @SerialName("failures")
    val failures: Map<UserId, JsonElement>?,
    @SerialName("device_keys")
    val deviceKeys: Map<UserId, Map<String, SignedDeviceKeys>>?,
    @SerialName("master_keys")
    val masterKeys: Map<UserId, SignedCrossSigningKeys>?,
    @SerialName("self_signing_keys")
    val selfSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
    @SerialName("user_signing_keys")
    val userSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
)