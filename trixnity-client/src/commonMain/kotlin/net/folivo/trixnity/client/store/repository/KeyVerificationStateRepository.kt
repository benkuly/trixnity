package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

interface KeyVerificationStateRepository : MinimalRepository<VerifiedKeysRepositoryKey, KeyVerificationState> {
    override fun serializeKey(key: VerifiedKeysRepositoryKey): String =
        this::class.simpleName + key.keyId + key.keyAlgorithm.name
}

data class VerifiedKeysRepositoryKey(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)