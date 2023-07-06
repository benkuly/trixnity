package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.core.model.RoomId

internal class RealmTimelineEvent : RealmObject {
    @PrimaryKey
    var id: String = ""

    var roomId: String = ""
    var eventId: String = ""
    var value: String = ""
}

internal class RealmTimelineEventRepository(
    private val json: Json,
) : TimelineEventRepository {
    override suspend fun get(key: TimelineEventKey): TimelineEvent? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            json.decodeFromString<TimelineEvent>(it.value)
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmTimelineEvent>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    override suspend fun save(key: TimelineEventKey, value: TimelineEvent): Unit = withRealmWrite {
        copyToRealm(
            RealmTimelineEvent().apply {
                id = serializeKey(key)
                roomId = key.roomId.full
                eventId = key.eventId.full
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
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
