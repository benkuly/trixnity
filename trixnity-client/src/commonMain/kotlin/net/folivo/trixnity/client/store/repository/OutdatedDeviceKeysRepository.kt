package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.UserId

typealias OutdatedKeysRepository = MinimalStoreRepository<Long, Set<UserId>>