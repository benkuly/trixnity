package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.repository.RoomRepository
import de.connect2x.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoom : Table("room") {
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedRoomRepository(private val json: Json) : RoomRepository {
    override suspend fun getAll(): List<Room> = withExposedRead {
        ExposedRoom.selectAll().map { json.decodeFromString(it[ExposedRoom.value]) }
    }

    override suspend fun get(key: RoomId): Room? = withExposedRead {
        ExposedRoom.selectAll().where { ExposedRoom.roomId eq key.full }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedRoom.value])
        }
    }

    override suspend fun save(key: RoomId, value: Room): Unit = withExposedWrite {
        ExposedRoom.upsert {
            it[roomId] = key.full
            it[ExposedRoom.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId): Unit = withExposedWrite {
        ExposedRoom.deleteWhere { roomId eq key.full }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoom.deleteAll()
    }
}