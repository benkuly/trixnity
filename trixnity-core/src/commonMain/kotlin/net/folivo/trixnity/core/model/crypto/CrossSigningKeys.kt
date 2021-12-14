package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class CrossSigningKeys(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("usage")
    val usage: Set<CrossSigningKeysUsage>,
    @SerialName("keys")
    val keys: Keys
)

typealias SignedCrossSigningKeys = Signed<CrossSigningKeys, UserId>