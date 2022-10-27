package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
data class StoredDeviceKeys(
    val value: SignedDeviceKeys,
    val trustLevel: KeySignatureTrustLevel
)
