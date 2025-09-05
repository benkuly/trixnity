package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.crypto.olm.StoredOlmSession

interface OlmSessionRepository : FullRepository<Curve25519KeyValue, Set<StoredOlmSession>> {
    override fun serializeKey(key: Curve25519KeyValue): String = key.value
}