package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

typealias VerifiedKeysRepository = MinimalStoreRepository<VerifiedKeysRepositoryKey, String>

data class VerifiedKeysRepositoryKey(
    val userId: UserId,
    val deviceId: String?,
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm
)