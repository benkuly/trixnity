package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

internal class RealmTimelineEventRelation : RealmObject {
    var roomId: String = ""
    var eventId: String = ""
    var relationType: String = ""
    var relatedEventId: String = ""
}

internal class RealmTimelineEventRelationRepository : TimelineEventRelationRepository {
    override suspend fun get(key: TimelineEventRelationKey): Map<RelationType, Set<TimelineEventRelation>>? =
        withRealmRead {
            findByKey(key).find()
                .groupBy { it.relationType }
                .map { (key, value) ->
                    val relationType = RelationType.of(key)
                    relationType to value.map {
                        TimelineEventRelation(
                            roomId = RoomId(it.roomId),
                            eventId = EventId(it.eventId),
                            relationType = relationType,
                            relatedEventId = EventId(it.relatedEventId)
                        )
                    }.toSet()
                }.toMap().ifEmpty { null }
        }

    override suspend fun getBySecondKey(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType
    ): Set<TimelineEventRelation>? = withRealmRead {
        findByKeys(firstKey, secondKey).find().map {
            TimelineEventRelation(
                roomId = RoomId(it.roomId),
                eventId = EventId(it.eventId),
                relationType = RelationType.of(it.relationType),
                relatedEventId = EventId(it.relatedEventId),
            )
        }.toSet().ifEmpty { null }
    }

    override suspend fun save(key: TimelineEventRelationKey, value: Map<RelationType, Set<TimelineEventRelation>>) =
        withRealmWrite {
            value.entries.flatMap { it.value }.forEach { timelineEventRelation ->
                saveToRealm(key, timelineEventRelation)
            }
        }

    override suspend fun saveBySecondKey(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType,
        value: Set<TimelineEventRelation>
    ) = withRealmWrite {
        value.forEach { timelineEventRelation ->
            saveToRealm(firstKey, timelineEventRelation)
        }
    }

    override suspend fun delete(key: TimelineEventRelationKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteBySecondKey(firstKey: TimelineEventRelationKey, secondKey: RelationType) =
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
        val existing = findByKeys(key, timelineEventRelation).find()
        val upsert = (existing ?: RealmTimelineEventRelation()).apply {
            roomId = key.roomId.full
            relatedEventId = key.relatedEventId.full
            relationType = timelineEventRelation.relationType.name
            eventId = timelineEventRelation.eventId.full
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
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

    private fun TypedRealm.findByKeys(
        key: TimelineEventRelationKey,
        timelineEventRelation: TimelineEventRelation
    ) = query<RealmTimelineEventRelation>(
        "roomId == $0 && relatedEventId == $1 && relationType == $2 && relatedEventId == $3",
        key.roomId.full,
        key.relatedEventId.full,
        timelineEventRelation.relationType.name,
        timelineEventRelation.relatedEventId.full,
    ).first()
}
