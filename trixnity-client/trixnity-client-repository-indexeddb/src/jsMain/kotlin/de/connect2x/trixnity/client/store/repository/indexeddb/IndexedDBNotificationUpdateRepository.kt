package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.core.model.RoomId

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBNotificationUpdateRepository(
    json: Json
) : NotificationUpdateRepository,
    IndexedDBFullRepository<String, StoredNotificationUpdate>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "notification_update"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 8) createIndexedDBMinimalStoreRepository(database, objectStoreName) {
                createIndex("roomId", KeyPath("roomId"), unique = false)
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