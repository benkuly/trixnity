package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.coroutines.CoroutineContext

class SqlDelightOutboundMegolmSessionRepository(
    private val db: OlmQueries,
    private val json: Json,
    private val context: CoroutineContext
) : OutboundMegolmSessionRepository {
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? = withContext(context) {
        db.getOutboundMegolmSession(key.full).executeAsOneOrNull()
            ?.let { json.decodeFromString(it) }
    }

    override suspend fun save(
        key: RoomId,
        value: StoredOutboundMegolmSession
    ) = withContext(context) {
        db.saveOutboundMegolmSession(key.full, json.encodeToString(value))
    }

    override suspend fun delete(key: RoomId) = withContext(context) {
        db.deleteOutboundMegolmSession(key.full)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllOutboundMegolmSessions()
    }
}