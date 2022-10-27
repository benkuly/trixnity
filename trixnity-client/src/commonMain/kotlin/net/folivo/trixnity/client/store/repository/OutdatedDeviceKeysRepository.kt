package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.UserId

interface OutdatedKeysRepository : MinimalStoreRepository<Long, Set<UserId>>