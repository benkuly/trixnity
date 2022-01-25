package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key
import kotlin.coroutines.CoroutineContext

class SqlDelightInboundMegolmSessionRepository(
    private val db: OlmQueries,
    private val json: Json,
    private val context: CoroutineContext
) : InboundMegolmSessionRepository {
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? =
        withContext(context) {
            db.getInboundMegolmSession(key.senderKey.value, key.sessionId, key.roomId.full)
                .executeAsOneOrNull()
                ?.mapToStoredInboundMegolmSession()
        }

    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> = withContext(context) {
        db.getNotBackedUpInboundMegolmSessions().executeAsList().map { it.mapToStoredInboundMegolmSession() }.toSet()
    }

    private fun Sql_inbound_megolm_session.mapToStoredInboundMegolmSession() = StoredInboundMegolmSession(
        senderKey = Key.Curve25519Key(null, this.sender_key),
        sessionId = this.session_id,
        roomId = RoomId(this.room_id),
        firstKnownIndex = this.first_known_index,
        hasBeenBackedUp = this.has_been_backed_up,
        isTrusted = this.is_trusted,
        senderSigningKey = Key.Ed25519Key(null, this.sender_signing_key),
        forwardingCurve25519KeyChain = json.decodeFromString(this.forwarding_curve25519_key_chain),
        pickled = this.pickled_session
    )

    override suspend fun save(
        key: InboundMegolmSessionRepositoryKey,
        value: StoredInboundMegolmSession
    ) = withContext(context) {
        db.saveInboundMegolmSession(
            sender_key = key.senderKey.value,
            session_id = key.sessionId,
            room_id = key.roomId.full,
            first_known_index = value.firstKnownIndex,
            has_been_backed_up = value.hasBeenBackedUp,
            is_trusted = value.isTrusted,
            sender_signing_key = value.senderSigningKey.value,
            forwarding_curve25519_key_chain = json.encodeToString(value.forwardingCurve25519KeyChain),
            pickled_session = value.pickled
        )
    }

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) = withContext(context) {
        db.deleteInboundMegolmSession(key.senderKey.value, key.sessionId, key.roomId.full)
    }
}