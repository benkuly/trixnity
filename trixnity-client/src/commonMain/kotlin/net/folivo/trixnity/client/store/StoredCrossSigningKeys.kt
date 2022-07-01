package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.client.key.KeySignatureTrustLevel
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys

@Serializable
data class StoredCrossSigningKeys(
    val value: SignedCrossSigningKeys,
    val trustLevel: KeySignatureTrustLevel
)
