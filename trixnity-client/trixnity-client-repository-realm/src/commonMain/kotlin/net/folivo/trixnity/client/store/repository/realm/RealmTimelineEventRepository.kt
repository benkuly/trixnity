package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository

internal class RealmTimelineEvent : RealmObject {
    var roomId: String = ""
    var eventId: String = ""
    var value: String = ""
}

internal class RealmTimelineEventRepository(
    private val json: Json,
) : TimelineEventRepository {
    override suspend fun get(key: TimelineEventKey): TimelineEvent? = withRealmRead {
        findByKey(key).find()?.let {
            json.decodeFromString<TimelineEvent>(it.value)
        }
    }

    override suspend fun save(key: TimelineEventKey, value: TimelineEvent) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmTimelineEvent()).apply {
            roomId = key.roomId.full
            eventId = key.eventId.full
            this.value = json.encodeToString(value)
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
    }

    override suspend fun delete(key: TimelineEventKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmTimelineEvent>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: TimelineEventKey) =
        query<RealmTimelineEvent>("roomId == $0 && eventId == $1", key.roomId.full, key.eventId.full).first()
}
