package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

internal class RealmTimelineEventRelation : RealmObject {
    @PrimaryKey
    var id: String = ""
    var roomId: String = ""
    var eventId: String = ""
    var relationType: String = ""
    var relatedEventId: String = ""
    var relatesTo: String = ""
}

internal class RealmTimelineEventRelationRepository(private val json: Json) : TimelineEventRelationRepository {
    override suspend fun get(firstKey: TimelineEventRelationKey): Map<EventId, TimelineEventRelation> =
        withRealmRead {
            findByKey(firstKey).find().copyFromRealm()
                .associate {
                    val eventId = EventId(it.eventId)
                    eventId to TimelineEventRelation(
                        roomId = RoomId(it.roomId),
                        eventId = eventId,
                        relatesTo = json.decodeFromString(it.relatesTo)
                    )
                }
        }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmTimelineEventRelation>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId
    ): TimelineEventRelation? = withRealmRead {
        findByKeys(firstKey, secondKey).find().firstOrNull()?.copyFromRealm()?.let {
            TimelineEventRelation(
                roomId = RoomId(it.roomId),
                eventId = EventId(it.eventId),
                relatesTo = json.decodeFromString(it.relatesTo),
            )
        }
    }

    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
        value: TimelineEventRelation
    ) = withRealmWrite {
        saveToRealm(firstKey, value)
    }

    override suspend fun delete(firstKey: TimelineEventRelationKey, secondKey: EventId) =
        withRealmWrite {
            val existing = findByKeys(firstKey, secondKey)
            delete(existing)
        }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmTimelineEventRelation>().find()
        delete(existing)
    }

    private fun MutableRealm.saveToRealm(
        firstKey: TimelineEventRelationKey,
        timelineEventRelation: TimelineEventRelation
    ) {
        copyToRealm(
            RealmTimelineEventRelation().apply {
                id = serializeKey(firstKey, timelineEventRelation.eventId)
                roomId = firstKey.roomId.full
                relatedEventId = firstKey.relatedEventId.full
                relationType = timelineEventRelation.relatesTo.relationType.name
                eventId = timelineEventRelation.eventId.full
                relatesTo = json.encodeToString(timelineEventRelation.relatesTo)
            },
            UpdatePolicy.ALL
        )
    }

    private fun TypedRealm.findByKey(key: TimelineEventRelationKey) =
        query<RealmTimelineEventRelation>(
            "roomId == $0 && relatedEventId == $1 && relationType == $2",
            key.roomId.full,
            key.relatedEventId.full,
            key.relationType.name
        )

    private fun TypedRealm.findByKeys(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId
    ) = query<RealmTimelineEventRelation>(
        "roomId == $0 && relatedEventId == $1 && relationType == $2 && eventId == $3",
        firstKey.roomId.full,
        firstKey.relatedEventId.full,
        firstKey.relationType.name,
        secondKey.full
    )
}
