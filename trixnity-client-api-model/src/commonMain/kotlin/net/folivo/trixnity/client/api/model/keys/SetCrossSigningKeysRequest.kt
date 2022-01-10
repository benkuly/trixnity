package net.folivo.trixnity.client.api.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.CrossSigningKeys
import net.folivo.trixnity.core.model.crypto.Signed

@Serializable
data class SetCrossSigningKeysRequest(
    @SerialName("master_key")
    val masterKey: Signed<CrossSigningKeys, UserId>?,
    @SerialName("self_signing_key")
    val selfSigningKey: Signed<CrossSigningKeys, UserId>?,
    @SerialName("user_signing_key")
    val userSigningKey: Signed<CrossSigningKeys, UserId>?,
)