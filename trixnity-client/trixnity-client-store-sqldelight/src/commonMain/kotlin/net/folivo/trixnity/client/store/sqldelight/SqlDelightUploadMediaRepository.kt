package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.client.store.repository.UploadMediaRepository
import kotlin.coroutines.CoroutineContext

class SqlDelightUploadMediaRepository(
    private val db: MediaQueries,
    private val context: CoroutineContext
) : UploadMediaRepository {
    override suspend fun get(key: String): UploadCache? = withContext(context) {
        db.getUploadMedia(key).executeAsOneOrNull()?.let { result ->
            UploadCache(result.cache_uri, result.mxc_uri, result.content_type)
        }
    }

    override suspend fun save(key: String, value: UploadCache) = withContext(context) {
        db.saveUploadMedia(key, value.mxcUri, value.contentType.toString())
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteUploadMedia(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllUploadMedia()
    }
}