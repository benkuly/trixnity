package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.UserId

interface DeviceKeysRepository : MinimalRepository<UserId, Map<String, StoredDeviceKeys>> {
    override fun serializeKey(key: UserId): String = this::class.simpleName + key.full
}