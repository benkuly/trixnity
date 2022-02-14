package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomRepository(
    private val db: RoomQueries,
    private val json: Json,
    private val context: CoroutineContext
) : RoomRepository {
    override suspend fun getAll(): List<Room> = withContext(context) {
        db.getAllRooms().executeAsList().map {
            json.decodeFromString(it.room)
        }
    }

    override suspend fun get(key: RoomId): Room? = withContext(context) {
        db.getRoom(key.full).executeAsOneOrNull()?.let {
            json.decodeFromString(it.room)
        }
    }

    override suspend fun save(key: RoomId, value: Room) = withContext(context) {
        db.saveRoom(key.full, json.encodeToString(value))
    }

    override suspend fun delete(key: RoomId) = withContext(context) {
        db.deleteRoom(key.full)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllRooms()
    }
}