package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import kotlin.time.Instant

internal class IndexedDBOlmForgetFallbackKeyAfterRepository(
    json: Json,
) : OlmForgetFallbackKeyAfterRepository,
    IndexedDBFullRepository<Long, Instant>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "olm_forget_fallback_key_after"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}