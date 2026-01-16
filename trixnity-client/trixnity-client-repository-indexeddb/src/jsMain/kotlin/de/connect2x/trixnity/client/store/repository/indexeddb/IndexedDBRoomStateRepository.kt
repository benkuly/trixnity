package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.RoomStateRepository
import de.connect2x.trixnity.client.store.repository.RoomStateRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent

@Serializable
internal class IndexedDBRoomState(
    val roomId: String,
    val type: String,
    val stateKey: String,
    @Contextual
    val event: StateBaseEvent<*>,
)

internal class IndexedDBRoomStateRepository(
    json: Json
) : RoomStateRepository,
    IndexedDBMapRepository<RoomStateRepositoryKey, String, StateBaseEvent<*>, IndexedDBRoomState>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId|type",
        firstKeySerializer = { arrayOf(it.roomId.full, it.type) },
        secondKeySerializer = { arrayOf(it) },
        secondKeyDestructor = { it.stateKey },
        mapToRepresentation = { k1, k2, v -> IndexedDBRoomState(k1.roomId.full, k1.type, k2, v) },
        mapFromRepresentation = { it.event },
        representationSerializer = IndexedDBRoomState.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_state"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1)
                createIndexedDBTwoDimensionsStoreRepository(
                    database = database,
                    objectStoreName = objectStoreName,
                    keyPath = KeyPath("roomId", "type", "stateKey"),
                    firstKeyIndexName = "roomId|type",
                    firstKeyIndexKeyPath = KeyPath("roomId", "type"),
                ) {
                    createIndex("roomId", KeyPath("roomId"), unique = false)
                    createIndex("type|stateKey", KeyPath("type", "stateKey"), unique = false)
                }
        }
    }

    override suspend fun getByRooms(roomIds: Set<RoomId>, type: String, stateKey: String): List<StateBaseEvent<*>> =
        withIndexedDBRead { store ->
            val roomIdStrings = roomIds.map { it.full }
            store.index("type|stateKey").openCursor(keyOf(arrayOf(type, stateKey)), autoContinue = true)
                .mapNotNull { json.decodeFromDynamicNullable(representationSerializer, it.value) }
                .filter { roomIdStrings.contains(it.roomId) }
                .map { mapFromRepresentation(it) }
                .toList()
        }

    override suspend fun deleteByRoomId(roomId: RoomId) = withIndexedDBWrite { store ->
        store.index("roomId").openCursor(Key(roomId.full), autoContinue = true)
            .collect {
                store.delete(Key(it.primaryKey))
            }
    }
}