package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository

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
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 ->
                    createIndexedDBMinimalStoreRepository(database, objectStoreName)
            }
        }
    }
}