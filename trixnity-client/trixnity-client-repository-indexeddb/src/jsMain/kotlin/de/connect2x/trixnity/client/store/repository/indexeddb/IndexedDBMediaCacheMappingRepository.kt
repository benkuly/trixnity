package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.MediaCacheMapping
import de.connect2x.trixnity.client.store.repository.MediaCacheMappingRepository
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

internal class IndexedDBMediaCacheMappingRepository(
    json: Json,
) : MediaCacheMappingRepository,
    IndexedDBFullRepository<String, MediaCacheMapping>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "media_cache_mapping"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}