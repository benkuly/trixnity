package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository

internal class IndexedDBAuthenticationRepository(json: Json) : AuthenticationRepository,
    IndexedDBFullRepository<Long, Authentication>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "authentication"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 9) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}