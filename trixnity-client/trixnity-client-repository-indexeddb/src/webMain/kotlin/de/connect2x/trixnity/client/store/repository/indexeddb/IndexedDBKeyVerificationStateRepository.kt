package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateKey
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

internal class IndexedDBKeyVerificationStateRepository(
    json: Json
) : KeyVerificationStateRepository,
    IndexedDBFullRepository<KeyVerificationStateKey, KeyVerificationState>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.keyId, it.keyAlgorithm.name) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "key_verification_state"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}