package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys

typealias DeviceKeysRepository = MinimalStoreRepository<UserId, Map<String, DeviceKeys>>