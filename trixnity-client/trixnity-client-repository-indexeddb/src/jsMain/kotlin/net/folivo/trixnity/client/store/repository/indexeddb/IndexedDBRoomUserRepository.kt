package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
internal class IndexedDBRoomUser(
    val roomId: String,
    val userId: String,
    val value: RoomUser,
)

internal class IndexedDBRoomUserRepository(
    json: Json
) : RoomUserRepository,
    IndexedDBMapRepository<RoomId, UserId, RoomUser, IndexedDBRoomUser>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId",
        firstKeySerializer = { arrayOf(it.full) },
        secondKeySerializer = { arrayOf(it.full) },
        secondKeyDestructor = { UserId(it.userId) },
        mapToRepresentation = { k1, k2, v -> IndexedDBRoomUser(k1.full, k2.full, v) },
        mapFromRepresentation = { it.value },
        representationSerializer = IndexedDBRoomUser.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_user"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 ->
                    createIndexedDBTwoDimensionsStoreRepository(
                        database = database,
                        objectStoreName = objectStoreName,
                        keyPath = KeyPath("roomId", "userId"),
                        firstKeyIndexName = "roomId",
                        firstKeyIndexKeyPath = KeyPath("roomId"),
                    )
            }
        }
    }
}