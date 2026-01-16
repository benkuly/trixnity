package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.core.model.UserId

internal class IndexedDBOutdatedKeysRepository(
    json: Json
) : OutdatedKeysRepository,
    IndexedDBFullRepository<Long, Set<UserId>>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "outdated_keys"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}