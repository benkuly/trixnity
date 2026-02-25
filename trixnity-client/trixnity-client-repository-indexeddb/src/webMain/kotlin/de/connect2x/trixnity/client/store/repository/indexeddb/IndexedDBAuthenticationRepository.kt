package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

internal class IndexedDBAuthenticationRepository(json: Json) : AuthenticationRepository,
    IndexedDBFullRepository<Long, Authentication>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "authentication"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 9) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}