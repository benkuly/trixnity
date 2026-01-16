package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.repository.RoomUserReceiptsRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

@Serializable
internal class IndexedDBRoomUserReceipts(
    val roomId: String,
    val userId: String,
    val value: RoomUserReceipts,
)

internal class IndexedDBRoomUserReceiptsRepository(
    json: Json
) : RoomUserReceiptsRepository,
    IndexedDBMapRepository<RoomId, UserId, RoomUserReceipts, IndexedDBRoomUserReceipts>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId",
        firstKeySerializer = { arrayOf(it.full) },
        secondKeySerializer = { arrayOf(it.full) },
        secondKeyDestructor = { UserId(it.userId) },
        mapToRepresentation = { k1, k2, v -> IndexedDBRoomUserReceipts(k1.full, k2.full, v) },
        mapFromRepresentation = { it.value },
        representationSerializer = IndexedDBRoomUserReceipts.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_user_receipts"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 2)
                createIndexedDBTwoDimensionsStoreRepository(
                    database = database,
                    objectStoreName = objectStoreName,
                    keyPath = KeyPath("roomId", "userId"),
                    firstKeyIndexName = "roomId",
                    firstKeyIndexKeyPath = KeyPath("roomId"),
                )
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withIndexedDBWrite { store ->
        store.index(firstKeyIndexName).openCursor(Key(roomId.full), autoContinue = true)
            .collect {
                store.delete(Key(it.primaryKey))
            }
    }
}