package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedOutboundMegolmSession : Table("outbound_megolm_session") {
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedOutboundMegolmSessionRepository(private val json: Json) : OutboundMegolmSessionRepository {
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? {
        return ExposedOutboundMegolmSession.select { ExposedOutboundMegolmSession.roomId eq key.full }.firstOrNull()
            ?.let {
                json.decodeFromString(it[ExposedOutboundMegolmSession.value])
            }
    }

    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession) {
        ExposedOutboundMegolmSession.replace {
            it[roomId] = key.full
            it[this.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId) {
        ExposedOutboundMegolmSession.deleteWhere { ExposedOutboundMegolmSession.roomId eq key.full }
    }

    override suspend fun deleteAll() {
        ExposedOutboundMegolmSession.deleteAll()
    }
}