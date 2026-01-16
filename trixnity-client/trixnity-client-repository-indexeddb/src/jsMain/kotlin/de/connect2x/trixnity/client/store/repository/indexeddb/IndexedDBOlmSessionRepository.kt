package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.repository.OlmSessionRepository
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredOlmSession

internal class IndexedDBOlmSessionRepository(
    json: Json
) : OlmSessionRepository,
    IndexedDBFullRepository<Curve25519KeyValue, Set<StoredOlmSession>>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.value) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "olm_session"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}