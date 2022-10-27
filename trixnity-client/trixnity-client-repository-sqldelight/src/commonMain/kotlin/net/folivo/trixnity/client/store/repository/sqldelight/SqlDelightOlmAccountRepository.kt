package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.OlmAccountRepository
import net.folivo.trixnity.client.store.sqldelight.OlmQueries
import kotlin.coroutines.CoroutineContext

class SqlDelightOlmAccountRepository(
    private val db: OlmQueries,
    private val context: CoroutineContext
) : OlmAccountRepository {
    override suspend fun get(key: Long): String? = withContext(context) {
        db.getOlmAccount(key).executeAsOneOrNull()?.pickled_account
    }

    override suspend fun save(key: Long, value: String) = withContext(context) {
        db.saveOlmAccount(key, value)
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteOlmAccount(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllOlmAccounts()
    }
}