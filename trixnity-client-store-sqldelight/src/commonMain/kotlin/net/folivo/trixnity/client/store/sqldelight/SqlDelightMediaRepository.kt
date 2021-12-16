package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.MediaRepository
import kotlin.coroutines.CoroutineContext

class SqlDelightMediaRepository(
    private val db: MediaQueries,
    private val context: CoroutineContext
) : MediaRepository {
    override suspend fun changeUri(oldUri: String, newUri: String) = withContext(context) {
        db.changeUri(newUri, oldUri, oldUri)
    }

    override suspend fun get(key: String): ByteArray? = withContext(context) {
        db.getMedia(key).executeAsOneOrNull()
    }

    override suspend fun save(key: String, value: ByteArray) = withContext(context) {
        db.saveMedia(key, value)
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteMedia(key)
    }
}