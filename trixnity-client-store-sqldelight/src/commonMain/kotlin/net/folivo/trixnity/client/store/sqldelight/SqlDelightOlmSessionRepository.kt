package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredOlmSession
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.core.model.crypto.Key
import kotlin.coroutines.CoroutineContext

class SqlDelightOlmSessionRepository(
    private val db: OlmQueries,
    private val json: Json,
    private val context: CoroutineContext
) : OlmSessionRepository {
    override suspend fun get(key: Key.Curve25519Key): Set<StoredOlmSession>? = withContext(context) {
        db.getOlmSessions(key.value).executeAsOneOrNull()?.let { json.decodeFromString(it) }
    }

    override suspend fun save(key: Key.Curve25519Key, value: Set<StoredOlmSession>) = withContext(context) {
        db.saveOlmSessions(key.value, json.encodeToString(value))
    }

    override suspend fun delete(key: Key.Curve25519Key) = withContext(context) {
        db.deleteOlmSessions(key.value)
    }
}