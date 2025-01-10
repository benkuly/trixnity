package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository

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