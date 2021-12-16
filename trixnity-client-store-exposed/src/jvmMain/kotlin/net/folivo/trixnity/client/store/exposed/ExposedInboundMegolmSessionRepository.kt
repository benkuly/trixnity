package net.folivo.trixnity.client.store.exposed

import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import org.jetbrains.exposed.sql.*

internal object ExposedInboundMegolmSession : Table("inbound_megolm_session") {
    val senderKey = varchar("sender_key", length = 65535)
    val sessionId = varchar("session_id", length = 65535)
    val roomId = varchar("room_id", length = 65535)
    override val primaryKey = PrimaryKey(senderKey, sessionId, roomId)
    val pickled = text("pickled")
}

internal class ExposedInboundMegolmSessionRepository : InboundMegolmSessionRepository {
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? {
        return ExposedInboundMegolmSession.select {
            ExposedInboundMegolmSession.senderKey.eq(key.senderKey.value) and
                    ExposedInboundMegolmSession.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmSession.roomId.eq(key.roomId.full)
        }.firstOrNull()?.let {
            StoredInboundMegolmSession(
                key.senderKey, key.sessionId, key.roomId,
                it[ExposedInboundMegolmSession.pickled]
            )
        }
    }

    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession) {
        ExposedInboundMegolmSession.replace {
            it[senderKey] = value.senderKey.value
            it[sessionId] = value.sessionId
            it[roomId] = value.roomId.full
            it[pickled] = value.pickled
        }
    }

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) {
        ExposedInboundMegolmSession.deleteWhere {
            ExposedInboundMegolmSession.senderKey.eq(key.senderKey.value) and
                    ExposedInboundMegolmSession.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmSession.roomId.eq(key.roomId.full)
        }
    }
}