package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm

interface KeyVerificationStateRepository : MinimalRepository<KeyVerificationStateKey, KeyVerificationState> {
    override fun serializeKey(key: KeyVerificationStateKey): String =
        key.keyId + key.keyAlgorithm
}

data class KeyVerificationStateKey(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)