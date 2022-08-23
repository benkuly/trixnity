package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import mu.KotlinLogging
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val log = KotlinLogging.logger { }

internal class RealmInboundMegolmMessageIndex : RealmObject {
    var sessionId: String = ""
    var roomId: String = ""
    var messageIndex: Long = 0

    var eventId: String = ""
    var originTimestamp: Long = 0
}

@OptIn(ExperimentalTime::class)
internal class RealmInboundMegolmMessageIndexRepository(
    private val realm: Realm,
) : InboundMegolmMessageIndexRepository {
    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? {
        val result = measureTimedValue {
            realm.findByKey(key).find()?.let {
                StoredInboundMegolmMessageIndex(
                    sessionId = it.sessionId,
                    roomId = RoomId(it.roomId),
                    messageIndex = it.messageIndex,
                    eventId = EventId(it.eventId),
                    originTimestamp = it.originTimestamp,
                )
            }
        }
        log.debug { "GET: ${result.duration.inWholeMilliseconds}ms" }

        return result.value
    }

    override suspend fun save(key: InboundMegolmMessageIndexRepositoryKey, value: StoredInboundMegolmMessageIndex) {
        val time = measureTime {
            realm.write {
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
        }
        log.debug { "SAVE: ${time.inWholeMilliseconds}ms" }
    }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) {
        val time = measureTime {
            realm.write {
                val existing = findByKey(key)
                delete(existing)
            }
        }
        log.debug { "DELETE: ${time.inWholeMilliseconds}ms" }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmInboundMegolmMessageIndex>().find()
            delete(existing)
        }
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
