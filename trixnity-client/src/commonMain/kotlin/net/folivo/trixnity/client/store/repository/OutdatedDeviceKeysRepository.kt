package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.MatrixId

typealias OutdatedDeviceKeysRepository = MinimalStoreRepository<Long, Set<MatrixId.UserId>>