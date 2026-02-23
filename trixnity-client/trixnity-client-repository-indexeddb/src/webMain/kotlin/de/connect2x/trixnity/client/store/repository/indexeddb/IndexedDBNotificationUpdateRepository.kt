package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

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
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 8) createIndexedDBMinimalStoreRepository(
                database,
                objectStoreName,
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