package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredOlmSession

interface OlmSessionRepository : MinimalRepository<Key.Curve25519Key, Set<StoredOlmSession>> {
    override fun serializeKey(key: Key.Curve25519Key): String = this::class.simpleName + key.value
}