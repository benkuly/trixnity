package net.folivo.trixnity.client.store.exposed

import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedInboundMegolmMessageIndex : Table("inbound_megolm_message_index") {
    val sessionId = varchar("session_id", length = 250)
    val roomId = varchar("room_id", length = 255)
    val messageIndex = long("message_index")
    override val primaryKey = PrimaryKey(sessionId, roomId, messageIndex)
    val eventId = text("event_id")
    val origin_timestamp = long("origin_timestamp")
}

internal class ExposedInboundMegolmMessageIndexRepository : InboundMegolmMessageIndexRepository {
    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? {
        return ExposedInboundMegolmMessageIndex.select {
            ExposedInboundMegolmMessageIndex.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmMessageIndex.roomId.eq(key.roomId.full) and
                    ExposedInboundMegolmMessageIndex.messageIndex.eq(key.messageIndex)
        }.firstOrNull()?.let {
            StoredInboundMegolmMessageIndex(
                key.sessionId, key.roomId, key.messageIndex,
                EventId(it[ExposedInboundMegolmMessageIndex.eventId]),
                it[ExposedInboundMegolmMessageIndex.origin_timestamp]
            )
        }
    }

    override suspend fun save(
        key: InboundMegolmMessageIndexRepositoryKey,
        value: StoredInboundMegolmMessageIndex
    ) {
        ExposedInboundMegolmMessageIndex.replace {
            it[sessionId] = value.sessionId
            it[roomId] = value.roomId.full
            it[messageIndex] = value.messageIndex
            it[eventId] = value.eventId.full
            it[origin_timestamp] = value.originTimestamp
        }
    }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) {
        ExposedInboundMegolmMessageIndex.deleteWhere {
            ExposedInboundMegolmMessageIndex.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmMessageIndex.roomId.eq(key.roomId.full) and
                    ExposedInboundMegolmMessageIndex.messageIndex.eq(key.messageIndex)
        }
    }

    override suspend fun deleteAll() {
        ExposedInboundMegolmMessageIndex.deleteAll()
    }
}