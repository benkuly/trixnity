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
import net.folivo.trixnity.core.model.events.m.RelationType

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
    override suspend fun get(firstKey: TimelineEventRelationKey): Map<RelationType, Set<TimelineEventRelation>> =
        withRealmRead {
            findByKey(firstKey).find().copyFromRealm()
                .groupBy { it.relationType }
                .map { (key, value) ->
                    val relationType = RelationType.of(key)
                    relationType to value.map {
                        TimelineEventRelation(
                            roomId = RoomId(it.roomId),
                            eventId = EventId(it.eventId),
                            relatesTo = json.decodeFromString(it.relatesTo)
                        )
                    }.toSet()
                }.toMap()
        }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmTimelineEventRelation>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType
    ): Set<TimelineEventRelation>? = withRealmRead {
        findByKeys(firstKey, secondKey).find().copyFromRealm().map {
            TimelineEventRelation(
                roomId = RoomId(it.roomId),
                eventId = EventId(it.eventId),
                relatesTo = json.decodeFromString(it.relatesTo),
            )
        }.toSet().ifEmpty { null }
    }

    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType,
        value: Set<TimelineEventRelation>
    ) = withRealmWrite {
        value.forEach { timelineEventRelation ->
            saveToRealm(firstKey, timelineEventRelation)
        }
    }

    override suspend fun delete(firstKey: TimelineEventRelationKey, secondKey: RelationType) =
        withRealmWrite {
            val existing = findByKeys(firstKey, secondKey)
            delete(existing)
        }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmTimelineEventRelation>().find()
        delete(existing)
    }

    private fun MutableRealm.saveToRealm(
        key: TimelineEventRelationKey,
        timelineEventRelation: TimelineEventRelation
    ) {
        copyToRealm(
            RealmTimelineEventRelation().apply {
                id = serializeKey(key, timelineEventRelation.relatesTo.relationType)
                roomId = key.roomId.full
                relatedEventId = key.relatedEventId.full
                relationType = timelineEventRelation.relatesTo.relationType.name
                eventId = timelineEventRelation.eventId.full
                relatesTo = json.encodeToString(timelineEventRelation.relatesTo)
            },
            UpdatePolicy.ALL
        )
    }

    private fun TypedRealm.findByKey(key: TimelineEventRelationKey) =
        query<RealmTimelineEventRelation>(
            "roomId == $0 && relatedEventId == $1",
            key.roomId.full,
            key.relatedEventId.full
        )

    private fun TypedRealm.findByKeys(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType
    ) = query<RealmTimelineEventRelation>(
        "roomId == $0 && relatedEventId == $1 && relationType == $2",
        firstKey.roomId.full,
        firstKey.relatedEventId.full,
        secondKey.name
    )
}
