package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.UserId

typealias OutdatedDeviceKeysRepository = MinimalStoreRepository<Long, Set<UserId>>