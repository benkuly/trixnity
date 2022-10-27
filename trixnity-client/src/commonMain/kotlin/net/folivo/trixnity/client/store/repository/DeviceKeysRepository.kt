package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.UserId

interface DeviceKeysRepository : MinimalStoreRepository<UserId, Map<String, StoredDeviceKeys>>