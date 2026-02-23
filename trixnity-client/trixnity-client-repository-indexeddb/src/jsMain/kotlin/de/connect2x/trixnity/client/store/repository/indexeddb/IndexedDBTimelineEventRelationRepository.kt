package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationRepository
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

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
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1)
                createIndexedDBTwoDimensionsStoreRepository(
                    database = database,
                    objectStoreName = objectStoreName,
                    keyPath = KeyPath.Multiple("roomId", "relatedEventId", "relationType", "eventId"),
                    firstKeyIndexName = "roomId|relatedEventId|relationType",
                    firstKeyIndexKeyPath = KeyPath.Multiple("roomId", "relatedEventId", "relationType"),
                ) { store ->
                    store.createIndex("roomId", KeyPath.Single("roomId"), unique = false)
                }
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withIndexedDBWrite { store ->
        store.index("roomId").openCursor(keyOf(roomId.full))
            .collect {
                store.delete(it.primaryKey)
            }
    }
}