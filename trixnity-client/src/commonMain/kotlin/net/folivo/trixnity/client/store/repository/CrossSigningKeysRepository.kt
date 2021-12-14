package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredCrossSigningKey
import net.folivo.trixnity.core.model.UserId

typealias CrossSigningKeysRepository = MinimalStoreRepository<UserId, Set<StoredCrossSigningKey>>