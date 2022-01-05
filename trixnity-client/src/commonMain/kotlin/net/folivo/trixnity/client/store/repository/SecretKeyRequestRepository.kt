package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredSecretKeyRequest

interface SecretKeyRequestRepository : MinimalStoreRepository<String, StoredSecretKeyRequest> {
    suspend fun getAll(): List<StoredSecretKeyRequest>
}