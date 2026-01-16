package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.keys.SignedCrossSigningKeys

@Serializable
data class StoredCrossSigningKeys(
    val value: SignedCrossSigningKeys,
    val trustLevel: KeySignatureTrustLevel
)
