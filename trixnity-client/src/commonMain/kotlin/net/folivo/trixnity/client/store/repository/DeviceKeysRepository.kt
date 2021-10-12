package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.DeviceKeys

typealias DeviceKeysRepository = MinimalStoreRepository<MatrixId.UserId, Map<String, DeviceKeys>>