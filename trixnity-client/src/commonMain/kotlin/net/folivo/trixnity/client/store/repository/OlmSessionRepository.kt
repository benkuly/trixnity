package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredOlmSession
import net.folivo.trixnity.core.model.keys.Key

typealias OlmSessionRepository = MinimalStoreRepository<Key.Curve25519Key, Set<StoredOlmSession>>