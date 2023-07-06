package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import kotlin.time.ExperimentalTime

internal class RealmInboundMegolmMessageIndex : RealmObject {
    @PrimaryKey
    var id: String = ""

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
            findByKey(key).find()?.copyFromRealm()?.let {
                StoredInboundMegolmMessageIndex(
                    sessionId = it.sessionId,
                    roomId = RoomId(it.roomId),
                    messageIndex = it.messageIndex,
                    eventId = EventId(it.eventId),
                    originTimestamp = it.originTimestamp,
                )
            }
        }

    override suspend fun save(
        key: InboundMegolmMessageIndexRepositoryKey,
        value: StoredInboundMegolmMessageIndex
    ): Unit =
        withRealmWrite {
            copyToRealm(
                RealmInboundMegolmMessageIndex().apply {
                    id = serializeKey(key)
                    sessionId = key.sessionId
                    roomId = key.roomId.full
                    messageIndex = key.messageIndex
                    eventId = value.eventId.full
                    originTimestamp = value.originTimestamp
                },
                UpdatePolicy.ALL
            )
        }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmInboundMegolmMessageIndex>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: InboundMegolmMessageIndexRepositoryKey) =
        query<RealmInboundMegolmMessageIndex>(
            "sessionId == $0 && roomId == $1 && messageIndex == $2",
            key.sessionId,
            key.roomId.full,
            key.messageIndex
        ).first()
}
