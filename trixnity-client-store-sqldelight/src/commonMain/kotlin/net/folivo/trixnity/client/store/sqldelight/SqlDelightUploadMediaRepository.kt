package net.folivo.trixnity.client.store.sqldelight

import io.ktor.http.*
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.UploadMedia
import net.folivo.trixnity.client.store.repository.UploadMediaRepository
import kotlin.coroutines.CoroutineContext

class SqlDelightUploadMediaRepository(
    private val db: MediaQueries,
    private val context: CoroutineContext
) : UploadMediaRepository {
    override suspend fun get(key: String): UploadMedia? = withContext(context) {
        db.getUploadMedia(key).executeAsOneOrNull()?.let { result ->
            UploadMedia(result.cache_uri, result.mxc_uri, result.content_type?.let { ContentType.parse(it) })
        }
    }

    override suspend fun save(key: String, value: UploadMedia) = withContext(context) {
        db.saveUploadMedia(key, value.mxcUri, value.contentTyp.toString())
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteUploadMedia(key)
    }
}