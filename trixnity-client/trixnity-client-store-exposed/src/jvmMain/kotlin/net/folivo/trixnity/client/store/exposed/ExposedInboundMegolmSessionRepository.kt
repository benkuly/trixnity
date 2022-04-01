package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key
import org.jetbrains.exposed.sql.*

internal object ExposedInboundMegolmSession : Table("inbound_megolm_session") {
    val senderKey = varchar("sender_key", length = 16383)
    val sessionId = varchar("session_id", length = 16383)
    val roomId = varchar("room_id", length = 16383)
    override val primaryKey = PrimaryKey(senderKey, sessionId, roomId)
    val firstKnownIndex = long("first_known_index")
    val hasBeenBackedUp = bool("has_been_backed_up")
    val isTrusted = bool("is_trusted")
    val senderSigningKey = text("sender_signing_key")
    val forwardingCurve25519KeyChain = text("forwarding_curve25519_key_chain")
    val pickled = text("pickled")
}

internal class ExposedInboundMegolmSessionRepository(private val json: Json) : InboundMegolmSessionRepository {
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? {
        return ExposedInboundMegolmSession.select {
            ExposedInboundMegolmSession.senderKey.eq(key.senderKey.value) and
                    ExposedInboundMegolmSession.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmSession.roomId.eq(key.roomId.full)
        }.firstOrNull()?.mapToStoredInboundMegolmSession()
    }

    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> {
        return ExposedInboundMegolmSession.select { ExposedInboundMegolmSession.hasBeenBackedUp.eq(false) }
            .map { it.mapToStoredInboundMegolmSession() }
            .toSet()
    }

    private fun ResultRow.mapToStoredInboundMegolmSession() = StoredInboundMegolmSession(
        senderKey = Key.Curve25519Key(null, this[ExposedInboundMegolmSession.senderKey]),
        sessionId = this[ExposedInboundMegolmSession.sessionId],
        roomId = RoomId(this[ExposedInboundMegolmSession.roomId]),
        firstKnownIndex = this[ExposedInboundMegolmSession.firstKnownIndex],
        hasBeenBackedUp = this[ExposedInboundMegolmSession.hasBeenBackedUp],
        isTrusted = this[ExposedInboundMegolmSession.isTrusted],
        senderSigningKey = Key.Ed25519Key(null, this[ExposedInboundMegolmSession.senderSigningKey]),
        forwardingCurve25519KeyChain = json.decodeFromString(this[ExposedInboundMegolmSession.forwardingCurve25519KeyChain]),
        pickled = this[ExposedInboundMegolmSession.pickled]
    )

    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession) {
        ExposedInboundMegolmSession.replace {
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

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) {
        ExposedInboundMegolmSession.deleteWhere {
            ExposedInboundMegolmSession.senderKey.eq(key.senderKey.value) and
                    ExposedInboundMegolmSession.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmSession.roomId.eq(key.roomId.full)
        }
    }

    override suspend fun deleteAll() {
        ExposedInboundMegolmSession.deleteAll()
    }
}