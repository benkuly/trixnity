package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import kotlin.time.ExperimentalTime

internal class RealmInboundMegolmMessageIndex : RealmObject {
    var sessionId: String = ""
    var roomId: String = ""
    var messageIndex: Long = 0

    var eventId: String = ""
    var originTimestamp: Long = 0
}

@OptIn(ExperimentalTime::class)
internal class RealmInboundMegolmMessageIndexRepository : InboundMegolmMessageIndexRepository {
    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? =
        withRealmRead {
            findByKey(key).find()?.let {
                StoredInboundMegolmMessageIndex(
                    sessionId = it.sessionId,
                    roomId = RoomId(it.roomId),
                    messageIndex = it.messageIndex,
                    eventId = EventId(it.eventId),
                    originTimestamp = it.originTimestamp,
                )
            }
        }

    override suspend fun save(key: InboundMegolmMessageIndexRepositoryKey, value: StoredInboundMegolmMessageIndex) =
        withRealmWrite {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmInboundMegolmMessageIndex()).apply {
                sessionId = value.sessionId
                roomId = value.roomId.full
                messageIndex = value.messageIndex
                eventId = value.eventId.full
                originTimestamp = value.originTimestamp
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmInboundMegolmMessageIndex>().find()
        delete(existing)
    }

    private fun Realm.findByKey(key: InboundMegolmMessageIndexRepositoryKey) =
        query<RealmInboundMegolmMessageIndex>(
            "sessionId == $0 && roomId == $1 && messageIndex == $2",
            key.sessionId,
            key.roomId.full,
            key.messageIndex
        ).first()

    private fun MutableRealm.findByKey(key: InboundMegolmMessageIndexRepositoryKey) =
        query<RealmInboundMegolmMessageIndex>(
            "sessionId == $0 && roomId == $1 && messageIndex == $2",
            key.sessionId,
            key.roomId.full,
            key.messageIndex
        ).first()
}
