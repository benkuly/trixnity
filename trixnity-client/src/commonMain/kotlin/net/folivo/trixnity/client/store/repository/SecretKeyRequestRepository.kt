package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredSecretKeyRequest

interface SecretKeyRequestRepository : FullRepository<String, StoredSecretKeyRequest> {
    override fun serializeKey(key: String): String = key
}