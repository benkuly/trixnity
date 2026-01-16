package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredOlmSession

interface OlmSessionRepository : FullRepository<Curve25519KeyValue, Set<StoredOlmSession>> {
    override fun serializeKey(key: Curve25519KeyValue): String = key.value
}