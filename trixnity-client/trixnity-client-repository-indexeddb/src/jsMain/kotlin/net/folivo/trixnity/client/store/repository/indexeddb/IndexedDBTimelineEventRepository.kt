package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.core.model.RoomId

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
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 -> createIndexedDBMinimalStoreRepository(database, objectStoreName) {
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