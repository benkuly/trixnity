package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.repository.NotificationRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBNotificationRepository(
    json: Json
) : NotificationRepository,
    IndexedDBFullRepository<String, StoredNotification>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "notification"
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