package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.core.model.UserId

interface DeviceKeysRepository : MinimalRepository<UserId, Map<String, StoredDeviceKeys>> {
    override fun serializeKey(key: UserId): String = key.full
}