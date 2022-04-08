package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*

internal object ExposedRoom : Table("room") {
    val roomId = varchar("room_id", length = 16383)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedRoomRepository(private val json: Json) : RoomRepository {
    override suspend fun getAll(): List<Room> {
        return ExposedRoom.selectAll().map { json.decodeFromString(it[ExposedRoom.value]) }
    }

    override suspend fun get(key: RoomId): Room? {
        return ExposedRoom.select { ExposedRoom.roomId eq key.full }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedRoom.value])
        }
    }

    override suspend fun save(key: RoomId, value: Room) {
        ExposedRoom.replace {
            it[roomId] = key.full
            it[this.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId) {
        ExposedRoom.deleteWhere { ExposedRoom.roomId eq key.full }
    }

    override suspend fun deleteAll() {
        ExposedRoom.deleteAll()
    }
}