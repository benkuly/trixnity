package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelationType

@Serializable
internal class IndexedDBTimelineEventRelation(
    val eventId: String,
    val roomId: String,
    val relationType: String,
    val relatedEventId: String,
)

internal class IndexedDBTimelineEventRelationRepository(
    json: Json
) : TimelineEventRelationRepository,
    IndexedDBMapRepository<TimelineEventRelationKey, EventId, TimelineEventRelation, IndexedDBTimelineEventRelation>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId|relatedEventId|relationType",
        firstKeySerializer = { arrayOf(it.roomId.full, it.relatedEventId.full, it.relationType.name) },
        secondKeySerializer = { arrayOf(it.full) },
        secondKeyDestructor = { EventId(it.eventId) },
        mapToRepresentation = { k1, k2, _ ->
            IndexedDBTimelineEventRelation(
                eventId = k2.full,
                roomId = k1.roomId.full,
                relatedEventId = k1.relatedEventId.full,
                relationType = k1.relationType.name,
            )
        },
        mapFromRepresentation = {
            TimelineEventRelation(
                eventId = EventId(it.eventId),
                roomId = RoomId(it.roomId),
                relationType = RelationType.of(it.relationType),
                relatedEventId = EventId(it.relatedEventId)
            )
        },
        representationSerializer = IndexedDBTimelineEventRelation.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "timeline_event_relation"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 ->
                    createIndexedDBTwoDimensionsStoreRepository(
                        database = database,
                        objectStoreName = objectStoreName,
                        keyPath = KeyPath("roomId", "relatedEventId", "relationType", "eventId"),
                        firstKeyIndexName = "roomId|relatedEventId|relationType",
                        firstKeyIndexKeyPath = KeyPath("roomId", "relatedEventId", "relationType"),
                    ) {
                        createIndex("roomId", KeyPath("roomId"), unique = false)
                    }
            }
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withIndexedDBWrite { store ->
        store.index("roomId").openCursor(Key(roomId.full), autoContinue = true)
            .collect {
                store.delete(Key(it.primaryKey))
            }
    }
}