package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.core.model.crypto.SignedCrossSigningKeys

@Serializable
data class StoredCrossSigningKey(
    val value: SignedCrossSigningKeys,
    val trustLevel: KeySignatureTrustLevel
)
