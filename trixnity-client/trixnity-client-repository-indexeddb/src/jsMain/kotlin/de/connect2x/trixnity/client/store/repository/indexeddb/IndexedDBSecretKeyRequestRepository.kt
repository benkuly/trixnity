package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredSecretKeyRequest
import de.connect2x.trixnity.client.store.repository.SecretKeyRequestRepository
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

internal class IndexedDBSecretKeyRequestRepository(
    json: Json
) : SecretKeyRequestRepository,
    IndexedDBFullRepository<String, StoredSecretKeyRequest>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "secret_key_request"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}