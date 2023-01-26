package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository

internal class IndexedDBTimelineEventRepository(
    json: Json
) : TimelineEventRepository,
    IndexedDBFullRepository<TimelineEventKey, TimelineEvent>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.roomId.full, it.eventId.full) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "timeline_event"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBMinimalStoreRepository(database, oldVersion, objectStoreName)
    }
}