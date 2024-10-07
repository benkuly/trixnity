package net.folivo.trixnity.client.store.repository.exposed

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
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? = withExposedRead {
        ExposedOutboundMegolmSession.selectAll().where { ExposedOutboundMegolmSession.roomId eq key.full }.firstOrNull()
            ?.let {
                json.decodeFromString(it[ExposedOutboundMegolmSession.value])
            }
    }

    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession): Unit = withExposedWrite {
        ExposedOutboundMegolmSession.upsert {
            it[roomId] = key.full
            it[ExposedOutboundMegolmSession.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId): Unit = withExposedWrite {
        ExposedOutboundMegolmSession.deleteWhere { roomId eq key.full }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedOutboundMegolmSession.deleteAll()
    }
}