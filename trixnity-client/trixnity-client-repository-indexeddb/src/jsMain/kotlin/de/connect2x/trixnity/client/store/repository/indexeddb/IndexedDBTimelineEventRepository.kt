package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.core.model.RoomId

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBTimelineEventRepository(
    json: Json
) : TimelineEventRepository,
    IndexedDBFullRepository<TimelineEventKey, TimelineEvent>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.roomId.full, it.eventId.full) },
        valueSerializer = checkNotNull(json.serializersModule.getContextual(TimelineEvent::class)),
        json = json
    ) {
    companion object {
        const val objectStoreName = "timeline_event"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName) {
                createIndex("roomId", KeyPath("event.room_id"), unique = false)
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