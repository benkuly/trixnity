package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.OlmQueries
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import kotlin.coroutines.CoroutineContext

class SqlDelightInboundMegolmMessageIndexRepository(
    private val db: OlmQueries,
    private val context: CoroutineContext
) : InboundMegolmMessageIndexRepository {
    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? =
        withContext(context) {
            db.getInboundMegolmSessionIndex(
                key.sessionId, key.roomId.full, key.messageIndex
            ).executeAsOneOrNull()
                ?.let {
                    StoredInboundMegolmMessageIndex(
                        key.sessionId, key.roomId, key.messageIndex,
                        EventId(it.event_id),
                        it.origin_timestamp
                    )
                }
        }

    override suspend fun save(
        key: InboundMegolmMessageIndexRepositoryKey,
        value: StoredInboundMegolmMessageIndex
    ) = withContext(context) {
        db.saveInboundMegolmSessionIndex(
            value.sessionId, value.roomId.full, value.messageIndex,
            value.eventId.full,
            value.originTimestamp
        )
    }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) = withContext(context) {
        db.deleteInboundMegolmSessionIndex(
            key.sessionId, key.roomId.full, key.messageIndex
        )
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllInboundMegolmSessionIndex()
    }
}