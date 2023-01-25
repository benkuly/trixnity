package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

interface KeyVerificationStateRepository : MinimalRepository<KeyVerificationStateKey, KeyVerificationState> {
    override fun serializeKey(key: KeyVerificationStateKey): String =
        this::class.simpleName + key.keyId + key.keyAlgorithm
}

data class KeyVerificationStateKey(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)