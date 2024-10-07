package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomUser : Table("room_user") {
    val userId = varchar("user_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(userId, roomId)
    val value = text("value")
}

internal class ExposedRoomUserRepository(private val json: Json) : RoomUserRepository {
    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUser? = withExposedRead {
        ExposedRoomUser.selectAll()
            .where { ExposedRoomUser.roomId.eq(firstKey.full) and ExposedRoomUser.userId.eq(secondKey.full) }
            .firstOrNull()?.let {
                json.decodeFromString(it[ExposedRoomUser.value])
            }
    }

    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUser): Unit =
        withExposedWrite {
            ExposedRoomUser.upsert {
                it[roomId] = firstKey.full
                it[userId] = secondKey.full
                it[ExposedRoomUser.value] = json.encodeToString(value)
            }
        }

    override suspend fun delete(firstKey: RoomId, secondKey: UserId): Unit = withExposedWrite {
        ExposedRoomUser.deleteWhere { roomId.eq(firstKey.full) and userId.eq(secondKey.full) }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomUser.deleteWhere { this.roomId.eq(roomId.full) }
    }

    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUser> = withExposedRead {
        ExposedRoomUser.selectAll().where { ExposedRoomUser.roomId eq firstKey.full }
            .map { json.decodeFromString<RoomUser>(it[ExposedRoomUser.value]) }
            .associateBy { it.userId }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoomUser.deleteAll()
    }
}