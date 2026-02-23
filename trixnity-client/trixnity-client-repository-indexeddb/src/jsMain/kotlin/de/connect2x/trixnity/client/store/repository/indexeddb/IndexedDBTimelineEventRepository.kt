package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

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
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName) { store ->
                store.createIndex("roomId", KeyPath.Single("event.room_id"), unique = false)
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