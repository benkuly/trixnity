package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoom : Table("room") {
    val roomId = varchar("room_id", length = 255)
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
            it[ExposedRoom.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId) {
        ExposedRoom.deleteWhere { roomId eq key.full }
    }

    override suspend fun deleteAll() {
        ExposedRoom.deleteAll()
    }
}