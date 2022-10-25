package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.sqldelight.MediaQueries
import kotlin.coroutines.CoroutineContext

class SqlDelightMediaCacheMappingRepository(
    private val db: MediaQueries,
    private val context: CoroutineContext
) : MediaCacheMappingRepository {
    override suspend fun get(key: String): MediaCacheMapping? = withContext(context) {
        db.getUploadMedia(key).executeAsOneOrNull()?.let { result ->
            MediaCacheMapping(result.cache_uri, result.mxc_uri, result.size?.toInt(), result.content_type)
        }
    }

    override suspend fun save(key: String, value: MediaCacheMapping) = withContext(context) {
        db.saveUploadMedia(key, value.mxcUri, value.size?.toLong(), value.contentType.toString())
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteUploadMedia(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllUploadMedia()
    }
}