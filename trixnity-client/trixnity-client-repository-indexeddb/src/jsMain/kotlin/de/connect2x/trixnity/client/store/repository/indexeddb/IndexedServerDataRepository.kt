package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.repository.ServerDataRepository

internal class IndexedServerDataRepository(json: Json) : ServerDataRepository,
    IndexedDBFullRepository<Long, ServerData>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "server_data"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 5) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}