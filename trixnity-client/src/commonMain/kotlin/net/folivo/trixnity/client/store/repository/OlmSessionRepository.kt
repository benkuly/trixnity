package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredOlmSession

interface OlmSessionRepository : MinimalStoreRepository<Key.Curve25519Key, Set<StoredOlmSession>>