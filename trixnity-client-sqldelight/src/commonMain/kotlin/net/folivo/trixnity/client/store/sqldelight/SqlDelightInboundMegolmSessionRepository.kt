package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import kotlin.coroutines.CoroutineContext

class SqlDelightInboundMegolmSessionRepository(
    private val db: OlmQueries,
    private val context: CoroutineContext
) : InboundMegolmSessionRepository {
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? =
        withContext(context) {
            db.getInboundMegolmSession(key.senderKey.value, key.sessionId, key.roomId.full)
                .executeAsOneOrNull()
                ?.let { StoredInboundMegolmSession(key.senderKey, key.sessionId, key.roomId, it) }
        }

    override suspend fun save(
        key: InboundMegolmSessionRepositoryKey,
        value: StoredInboundMegolmSession
    ) = withContext(context) {
        db.saveInboundMegolmSession(key.senderKey.value, key.sessionId, key.roomId.full, value.pickle)
    }

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) = withContext(context) {
        db.deleteInboundMegolmSession(key.senderKey.value, key.sessionId, key.roomId.full)
    }
}