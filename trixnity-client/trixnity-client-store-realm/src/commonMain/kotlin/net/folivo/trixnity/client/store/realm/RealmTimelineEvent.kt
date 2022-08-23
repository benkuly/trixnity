package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository

private val log = KotlinLogging.logger { }

internal class RealmTimelineEvent : RealmObject {
    var roomId: String = ""
    var eventId: String = ""
    var value: String = ""
}

internal class RealmTimelineEventRepository(
    private val realm: Realm,
    private val json: Json,
) : TimelineEventRepository {
    override suspend fun get(key: TimelineEventKey): TimelineEvent? {
        return realm.findByKey(key).find()?.let {
            json.decodeFromString<TimelineEvent>(it.value)
        }
    }

    override suspend fun save(key: TimelineEventKey, value: TimelineEvent) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmTimelineEvent()).apply {
                roomId = value.roomId.full
                eventId = value.eventId.full
                this.value = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: TimelineEventKey) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmTimelineEvent>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: TimelineEventKey) =
        query<RealmTimelineEvent>("roomId == $0 && eventId == $1", key.roomId.full, key.eventId.full).first()

    private fun MutableRealm.findByKey(key: TimelineEventKey) =
        query<RealmTimelineEvent>("roomId == $0 && eventId == $1", key.roomId.full, key.eventId.full).first()
}
