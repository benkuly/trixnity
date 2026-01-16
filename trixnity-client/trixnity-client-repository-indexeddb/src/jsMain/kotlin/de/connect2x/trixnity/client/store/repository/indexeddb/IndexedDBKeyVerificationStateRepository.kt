package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateKey
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository

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
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}