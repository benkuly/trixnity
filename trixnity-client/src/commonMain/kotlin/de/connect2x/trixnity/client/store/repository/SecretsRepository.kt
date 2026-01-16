package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.crypto.SecretType

interface SecretsRepository : MinimalRepository<Long, Map<SecretType, StoredSecret>> {
    override fun serializeKey(key: Long): String = key.toString()
}