package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

typealias KeyVerificationStateRepository = MinimalStoreRepository<VerifiedKeysRepositoryKey, KeyVerificationState>

data class VerifiedKeysRepositoryKey(
    val userId: UserId,
    val deviceId: String?,
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)