package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.core.model.UserId

internal class IndexedDBDeviceKeysRepository(
    json: Json
) : DeviceKeysRepository,
    IndexedDBFullRepository<UserId, Map<String, StoredDeviceKeys>>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.full) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "device_keys"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}