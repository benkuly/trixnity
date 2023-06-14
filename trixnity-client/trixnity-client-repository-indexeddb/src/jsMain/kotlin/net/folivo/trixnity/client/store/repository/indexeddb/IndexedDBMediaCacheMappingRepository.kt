package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository

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
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 ->
                    createIndexedDBMinimalStoreRepository(database, objectStoreName)
            }
        }
    }
}