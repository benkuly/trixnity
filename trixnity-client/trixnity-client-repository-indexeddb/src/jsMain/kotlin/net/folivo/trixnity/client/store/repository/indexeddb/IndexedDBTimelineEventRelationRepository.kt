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
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

@Serializable
internal class IndexedDBTimelineEventRelation(
    val roomId: String,
    val relatedEventId: String,
    val relationType: String,
    val relations: Set<TimelineEventRelation>,
)

internal class IndexedDBTimelineEventRelationRepository(
    json: Json
) : TimelineEventRelationRepository,
    IndexedDBMapRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>, IndexedDBTimelineEventRelation>(
        objectStoreName = IndexedDBRoomAccountDataRepository.objectStoreName,
        firstKeyIndexName = "roomId",
        firstKeySerializer = { arrayOf(it.roomId.full, it.relatedEventId.full) },
        secondKeySerializer = { arrayOf(it.name) },
        secondKeyDestructor = { RelationType.of(it.relationType) },
        mapToRepresentation = { k1, k2, v ->
            IndexedDBTimelineEventRelation(
                k1.roomId.full,
                k1.relatedEventId.full,
                k2.name,
                v
            )
        },
        mapFromRepresentation = { it.relations },
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
                        objectStoreName = IndexedDBRoomAccountDataRepository.objectStoreName,
                        keyPath = KeyPath("roomId", "relatedEventId", "relationType"),
                        firstKeyIndexName = "roomId",
                        firstKeyIndexKeyPath = KeyPath("roomId"),
                    ) {
                        createIndex("roomId", KeyPath("roomId"), unique = false)
                    }
            }
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withIndexedDBWrite { store ->
        store.index("roomId").openCursor(Key(roomId.full), autoContinue = true)
            .collect {
                store.delete(it.key as Key)
            }
    }
}