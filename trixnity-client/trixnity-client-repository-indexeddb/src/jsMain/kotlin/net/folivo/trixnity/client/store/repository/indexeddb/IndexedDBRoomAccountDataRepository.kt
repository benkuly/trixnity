package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent

@Serializable
internal class IndexedDBRoomAccountData(
    val roomId: String,
    val type: String,
    val key: String,
    @Contextual
    val value: RoomAccountDataEvent<*>,
)

internal class IndexedDBRoomAccountDataRepository(
    json: Json
) : RoomAccountDataRepository,
    IndexedDBMapRepository<RoomAccountDataRepositoryKey, String, RoomAccountDataEvent<*>, IndexedDBRoomAccountData>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId|type",
        firstKeySerializer = { arrayOf(it.roomId.full, it.type) },
        secondKeySerializer = { arrayOf(it) },
        secondKeyDestructor = { it.key },
        mapToRepresentation = { k1, k2, v -> IndexedDBRoomAccountData(k1.roomId.full, k1.type, k2, v) },
        mapFromRepresentation = { it.value },
        representationSerializer = IndexedDBRoomAccountData.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_account_data"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 ->
                    createIndexedDBTwoDimensionsStoreRepository(
                        database = database,
                        objectStoreName = objectStoreName,
                        keyPath = KeyPath("roomId", "type", "key"),
                        firstKeyIndexName = "roomId|type",
                        firstKeyIndexKeyPath = KeyPath("roomId", "type"),
                    ) {
                        createIndex("roomId", KeyPath("roomId"), unique = false)
                    }
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