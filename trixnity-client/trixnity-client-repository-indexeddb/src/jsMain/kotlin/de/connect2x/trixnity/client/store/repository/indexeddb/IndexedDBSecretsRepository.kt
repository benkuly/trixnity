package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.crypto.SecretType

internal class IndexedDBSecretsRepository(
    json: Json
) : SecretsRepository,
    IndexedDBFullRepository<Long, Map<SecretType, StoredSecret>>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "secret"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}