package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.crypto.SecretType

interface SecretsRepository : MinimalRepository<Long, Map<SecretType, StoredSecret>> {
    override fun serializeKey(key: Long): String = key.toString()
}