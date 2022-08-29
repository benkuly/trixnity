package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

interface KeyVerificationStateRepository : MinimalStoreRepository<VerifiedKeysRepositoryKey, KeyVerificationState>

data class VerifiedKeysRepositoryKey(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)