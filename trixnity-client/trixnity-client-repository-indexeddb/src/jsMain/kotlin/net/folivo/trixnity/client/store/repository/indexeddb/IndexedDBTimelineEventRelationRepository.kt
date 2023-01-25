package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.events.RelationType

internal class IndexedDBTimelineEventRelationRepository(
    json: Json
) : TimelineEventRelationRepository,
    IndexedDBTwoDimensionsRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>>(
        objectStoreName = objectStoreName,
        firstKeySerializer = { arrayOf(it.roomId.full, it.relatedEventId.full) },
        secondKeySerializer = { arrayOf(it.name) },
        secondKeyDeserializer = { RelationType.of(it.first()) },
        valueSerializer = serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "timeline_event_relation"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBTwoDimensionsStoreRepository(database, oldVersion, objectStoreName)
    }
}