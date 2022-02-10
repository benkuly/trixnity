package net.folivo.trixnity.client.api.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys

@Serializable
data class SetCrossSigningKeysRequest(
    @SerialName("master_key")
    val masterKey: SignedCrossSigningKeys?,
    @SerialName("self_signing_key")
    val selfSigningKey: SignedCrossSigningKeys?,
    @SerialName("user_signing_key")
    val userSigningKey: SignedCrossSigningKeys?,
)