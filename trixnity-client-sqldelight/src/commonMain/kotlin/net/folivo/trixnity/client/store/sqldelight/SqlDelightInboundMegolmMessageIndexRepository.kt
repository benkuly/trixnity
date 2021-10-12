package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.MatrixId
import kotlin.coroutines.CoroutineContext

class SqlDelightInboundMegolmMessageIndexRepository(
    private val db: OlmQueries,
    private val context: CoroutineContext
) : InboundMegolmMessageIndexRepository {
    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? =
        withContext(context) {
            db.getInboundMegolmSessionIndex(
                key.senderKey.value, key.sessionId, key.roomId.full, key.messageIndex
            ).executeAsOneOrNull()
                ?.let {
                    StoredInboundMegolmMessageIndex(
                        key.senderKey, key.sessionId, key.roomId, key.messageIndex,
                        MatrixId.EventId(it.event_id),
                        it.origin_timestamp
                    )
                }
        }

    override suspend fun save(
        key: InboundMegolmMessageIndexRepositoryKey,
        value: StoredInboundMegolmMessageIndex
    ) = withContext(context) {
        db.saveInboundMegolmSessionIndex(
            value.senderKey.value, value.sessionId, value.roomId.full, value.messageIndex,
            value.eventId.full,
            value.originTimestamp
        )
    }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) = withContext(context) {
        db.deleteInboundMegolmSessionIndex(
            key.senderKey.value, key.sessionId, key.roomId.full, key.messageIndex
        )
    }
}