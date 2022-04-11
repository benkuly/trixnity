package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

typealias KeyVerificationStateRepository = MinimalStoreRepository<VerifiedKeysRepositoryKey, KeyVerificationState>

data class VerifiedKeysRepositoryKey(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)