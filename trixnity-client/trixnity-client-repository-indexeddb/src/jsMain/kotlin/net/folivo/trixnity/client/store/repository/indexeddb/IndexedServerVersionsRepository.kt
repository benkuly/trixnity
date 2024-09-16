package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.ServerVersions
import net.folivo.trixnity.client.store.repository.ServerVersionsRepository

internal class IndexedServerVersionsRepository(json: Json) : ServerVersionsRepository,
    IndexedDBFullRepository<Long, ServerVersions>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "server_versions"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 4 ->
                    createIndexedDBMinimalStoreRepository(database, objectStoreName)
            }
        }
    }
}