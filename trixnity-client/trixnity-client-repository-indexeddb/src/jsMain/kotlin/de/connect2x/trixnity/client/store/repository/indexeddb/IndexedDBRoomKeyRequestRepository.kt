package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredRoomKeyRequest
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository

internal class IndexedDBRoomKeyRequestRepository(
    json: Json
) : RoomKeyRequestRepository,
    IndexedDBFullRepository<String, StoredRoomKeyRequest>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "room_key_request"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}