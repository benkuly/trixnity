package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedInboundMegolmSession : Table("inbound_megolm_session") {
    val senderKey = varchar("sender_key", length = 255)
    val sessionId = varchar("session_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(senderKey, sessionId, roomId)
    val firstKnownIndex = long("first_known_index")
    val hasBeenBackedUp = bool("has_been_backed_up")
    val isTrusted = bool("is_trusted")
    val senderSigningKey = text("sender_signing_key")
    val forwardingCurve25519KeyChain = text("forwarding_curve25519_key_chain")
    val pickled = text("pickled")
}

internal class ExposedInboundMegolmSessionRepository(private val json: Json) : InboundMegolmSessionRepository {
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? = withExposedRead {
        ExposedInboundMegolmSession.selectAll().where {
            ExposedInboundMegolmSession.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmSession.roomId.eq(key.roomId.full)
        }.firstOrNull()?.mapToStoredInboundMegolmSession()
    }

    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> = withExposedRead {
        ExposedInboundMegolmSession.selectAll().where { ExposedInboundMegolmSession.hasBeenBackedUp.eq(false) }
            .map { it.mapToStoredInboundMegolmSession() }
            .toSet()
    }

    private fun ResultRow.mapToStoredInboundMegolmSession() = StoredInboundMegolmSession(
        senderKey = Curve25519KeyValue(this[ExposedInboundMegolmSession.senderKey]),
        sessionId = this[ExposedInboundMegolmSession.sessionId],
        roomId = RoomId(this[ExposedInboundMegolmSession.roomId]),
        firstKnownIndex = this[ExposedInboundMegolmSession.firstKnownIndex],
        hasBeenBackedUp = this[ExposedInboundMegolmSession.hasBeenBackedUp],
        isTrusted = this[ExposedInboundMegolmSession.isTrusted],
        senderSigningKey = Ed25519KeyValue(this[ExposedInboundMegolmSession.senderSigningKey]),
        forwardingCurve25519KeyChain = json.decodeFromString(this[ExposedInboundMegolmSession.forwardingCurve25519KeyChain]),
        pickled = this[ExposedInboundMegolmSession.pickled]
    )

    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession): Unit =
        withExposedWrite {
            ExposedInboundMegolmSession.upsert {
                it[senderKey] = value.senderKey.value
                it[sessionId] = value.sessionId
                it[roomId] = value.roomId.full
                it[firstKnownIndex] = value.firstKnownIndex
                it[hasBeenBackedUp] = value.hasBeenBackedUp
                it[isTrusted] = value.isTrusted
                it[senderSigningKey] = value.senderSigningKey.value
                it[forwardingCurve25519KeyChain] = json.encodeToString(value.forwardingCurve25519KeyChain)
                it[pickled] = value.pickled
            }
        }

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey): Unit = withExposedWrite {
        ExposedInboundMegolmSession.deleteWhere {
            sessionId.eq(key.sessionId) and
                    roomId.eq(key.roomId.full)
        }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedInboundMegolmSession.deleteAll()
    }
}