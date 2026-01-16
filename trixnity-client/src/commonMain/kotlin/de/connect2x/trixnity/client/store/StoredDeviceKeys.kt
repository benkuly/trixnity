package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
data class StoredDeviceKeys(
    val value: SignedDeviceKeys,
    val trustLevel: KeySignatureTrustLevel
)
