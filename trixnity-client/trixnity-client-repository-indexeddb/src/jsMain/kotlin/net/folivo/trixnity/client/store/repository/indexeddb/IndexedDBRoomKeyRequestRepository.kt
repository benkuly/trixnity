package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.StoredRoomKeyRequest
import net.folivo.trixnity.client.store.repository.RoomKeyRequestRepository

internal class IndexedDBRoomKeyRequestRepository(
    json: Json
) : RoomKeyRequestRepository,
    IndexedDBMinimalStoreRepository<String, StoredRoomKeyRequest>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "room_key_request"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBMinimalStoreRepository(database, oldVersion, objectStoreName)
    }

    override suspend fun getAll(): List<StoredRoomKeyRequest> = withIndexedDBRead { store ->
        store.openCursor(autoContinue = true)
            .mapNotNull { json.decodeFromDynamicNullable<StoredRoomKeyRequest>(it.value) }
            .toList()
    }
}