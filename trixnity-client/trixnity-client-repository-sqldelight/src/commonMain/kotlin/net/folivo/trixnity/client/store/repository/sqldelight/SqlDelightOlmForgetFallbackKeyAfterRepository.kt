package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import net.folivo.trixnity.client.store.sqldelight.OlmQueries
import kotlin.coroutines.CoroutineContext

class SqlDelightOlmForgetFallbackKeyAfterRepository(
    private val db: OlmQueries,
    private val context: CoroutineContext
) : OlmForgetFallbackKeyAfterRepository {
    override suspend fun get(key: Long): Instant? = withContext(context) {
        db.getOlmForgetFallbackKeyAfter(key).executeAsOneOrNull()?.value_?.let { Instant.fromEpochMilliseconds(it) }
    }

    override suspend fun save(key: Long, value: Instant) = withContext(context) {
        db.saveOlmForgetFallbackKeyAfter(key, value.toEpochMilliseconds())
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteOlmForgetFallbackKeyAfter(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllOlmForgetFallbackKeyAfter()
    }
}