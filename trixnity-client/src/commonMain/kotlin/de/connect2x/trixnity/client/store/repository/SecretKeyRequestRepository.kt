package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredSecretKeyRequest

interface SecretKeyRequestRepository : FullRepository<String, StoredSecretKeyRequest> {
    override fun serializeKey(key: String): String = key
}