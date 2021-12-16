package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedOutboundMegolmSession : Table("outbound_megolm_session") {
    val roomId = varchar("room_id", length = 65535)
    override val primaryKey = PrimaryKey(roomId)
    val outboundMegolmSession = text("outbound_megolm_session")
}

internal class ExposedOutboundMegolmSessionRepository(private val json: Json) : OutboundMegolmSessionRepository {
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? {
        return ExposedOutboundMegolmSession.select { ExposedOutboundMegolmSession.roomId eq key.full }.firstOrNull()
            ?.let {
                json.decodeFromString(it[ExposedOutboundMegolmSession.outboundMegolmSession])
            }
    }

    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession) {
        ExposedOutboundMegolmSession.replace {
            it[roomId] = key.full
            it[outboundMegolmSession] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId) {
        ExposedOutboundMegolmSession.deleteWhere { ExposedOutboundMegolmSession.roomId eq key.full }
    }
}