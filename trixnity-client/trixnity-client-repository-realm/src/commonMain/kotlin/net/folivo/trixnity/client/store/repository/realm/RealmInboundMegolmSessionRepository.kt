package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import kotlin.time.ExperimentalTime

internal class RealmInboundMegolmSession : RealmObject {
    var senderKey: String = ""
    var sessionId: String = ""
    var roomId: String = ""
    var firstKnownIndex: Long = 0
    var hasBeenBackedUp: Boolean = false
    var isTrusted: Boolean = false
    var senderSigningKey: String = ""
    var forwardingCurve25519KeyChain: String = ""
    var pickled: String = ""
}

@OptIn(ExperimentalTime::class)
internal class RealmInboundMegolmSessionRepository(
    private val json: Json,
) : InboundMegolmSessionRepository {
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? = withRealmRead {
        findByKey(key).find()?.mapToStoredInboundMegolmSession()
    }

    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> = withRealmRead {
        query<RealmInboundMegolmSession>("hasBeenBackedUp == false").find()
            .map { it.mapToStoredInboundMegolmSession() }
            .toSet()
    }

    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession) =
        withRealmWrite {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmInboundMegolmSession()).apply {
                sessionId = key.sessionId
                roomId = key.roomId.full
                senderKey = value.senderKey.value
                firstKnownIndex = value.firstKnownIndex
                hasBeenBackedUp = value.hasBeenBackedUp
                isTrusted = value.isTrusted
                senderSigningKey = value.senderSigningKey.value
                forwardingCurve25519KeyChain = json.encodeToString(value.forwardingCurve25519KeyChain)
                pickled = value.pickled
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmInboundMegolmSession>().find()
        delete(existing)
    }

    private fun RealmInboundMegolmSession.mapToStoredInboundMegolmSession() =
        StoredInboundMegolmSession(
            senderKey = Key.Curve25519Key(null, this.senderKey),
            sessionId = this.sessionId,
            roomId = RoomId(this.roomId),
            firstKnownIndex = this.firstKnownIndex,
            hasBeenBackedUp = this.hasBeenBackedUp,
            isTrusted = this.isTrusted,
            senderSigningKey = Key.Ed25519Key(null, this.senderSigningKey),
            forwardingCurve25519KeyChain = json.decodeFromString(this.forwardingCurve25519KeyChain),
            pickled = this.pickled
        )

    private fun TypedRealm.findByKey(key: InboundMegolmSessionRepositoryKey) =
        query<RealmInboundMegolmSession>("sessionId == $0 && roomId == $1", key.sessionId, key.roomId.full).first()
}
