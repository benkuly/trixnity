package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredOlmSession

internal class IndexedDBOlmSessionRepository(
    json: Json
) : OlmSessionRepository,
    IndexedDBFullRepository<Key.Curve25519Key, Set<StoredOlmSession>>(
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